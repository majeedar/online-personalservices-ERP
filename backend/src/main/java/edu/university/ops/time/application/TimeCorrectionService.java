package edu.university.ops.time.application;

import edu.university.ops.employee.EmployeeDirectory;
import edu.university.ops.shared.audit.AuditService;
import edu.university.ops.shared.configuration.OpsProperties;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.shared.notification.NotificationService;
import edu.university.ops.shared.notification.NotificationType;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.security.Role;
import edu.university.ops.shared.workflow.ApprovalType;
import edu.university.ops.shared.workflow.DelegationService;
import edu.university.ops.shared.workflow.StepSpec;
import edu.university.ops.shared.workflow.WorkflowEnums.Decision;
import edu.university.ops.shared.workflow.WorkflowEnums.InstanceStatus;
import edu.university.ops.shared.workflow.WorkflowEvents;
import edu.university.ops.shared.workflow.WorkflowService;
import edu.university.ops.time.domain.TimeCorrectionRequest;
import edu.university.ops.time.domain.TimeCorrectionRequest.Operation;
import edu.university.ops.time.domain.TimeEntry;
import edu.university.ops.time.domain.TimeRepositories.TimeCorrectionRepository;
import edu.university.ops.time.domain.TimeRepositories.TimeEntryRepository;
import edu.university.ops.time.domain.TimeSequence;
import edu.university.ops.time.domain.TimeSequence.Event;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Time corrections with approval (AGENT.md §15.5, Demo Scenario 6). */
@Service
@Transactional
public class TimeCorrectionService {

    public static final String BUSINESS_OBJECT_TYPE = "TimeCorrectionRequest";
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public record CorrectionInput(LocalDate date, Operation operation, UUID originalEntryId, LocalTime requestedTime,
                                  TimeEntry.Type requestedType, String reason) {
    }

    private final TimeCorrectionRepository corrections;
    private final TimeEntryRepository entries;
    private final TimeAccountService accounts;
    private final EmployeeDirectory employees;
    private final WorkflowService workflow;
    private final DelegationService delegations;
    private final NotificationService notifications;
    private final AuditService audit;
    private final OpsProperties properties;
    private final Clock clock;

    TimeCorrectionService(TimeCorrectionRepository corrections, TimeEntryRepository entries,
                          TimeAccountService accounts, EmployeeDirectory employees, WorkflowService workflow,
                          DelegationService delegations, NotificationService notifications, AuditService audit,
                          OpsProperties properties, Clock clock) {
        this.corrections = corrections;
        this.entries = entries;
        this.accounts = accounts;
        this.employees = employees;
        this.workflow = workflow;
        this.delegations = delegations;
        this.notifications = notifications;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    public TimeCorrectionRequest request(OpsPrincipal principal, CorrectionInput input) {
        UUID employeeId = principal.employeeId();
        LocalDate today = LocalDate.now(clock);
        if (input.date() == null || input.date().isAfter(today)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Corrections are possible for past days only.");
        }
        requireOpenMonth(input.date());
        if (input.operation() == null || !StringUtils.hasText(input.reason())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Operation and reason are required.");
        }
        if (corrections.existsByEmployeeIdAndDateAndStatus(employeeId, input.date(),
                TimeCorrectionRequest.Status.IN_APPROVAL)) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE,
                    "There is already a pending correction for this day.");
        }
        Instant requested = null;
        if (input.operation() != Operation.DELETE) {
            if (input.requestedTime() == null || input.requestedType() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Time and entry type are required.");
            }
            requested = ZonedDateTime.of(input.date(), input.requestedTime(), properties.timezone()).toInstant();
        }
        if (input.operation() != Operation.ADD) {
            TimeEntry original = input.originalEntryId() == null ? null
                    : entries.findById(input.originalEntryId()).orElse(null);
            if (original == null || !original.getEmployeeId().equals(employeeId)
                    || !original.getBusinessDate().equals(input.date()) || original.isVoided()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose an existing entry of that day.");
            }
        }
        TimeCorrectionRequest request = new TimeCorrectionRequest(employeeId, input.date(), input.operation(),
                input.operation() == Operation.ADD ? null : input.originalEntryId(), requested,
                input.operation() == Operation.DELETE ? null : input.requestedType(), input.reason().strip(),
                Instant.now(clock));
        requireValidResult(request);
        corrections.save(request);

        String name = employees.findEmployee(employeeId).map(EmployeeDirectory.EmployeeSummary::displayName)
                .orElse("Unknown");
        List<UUID> approvers = employees.approversOf(employeeId, ApprovalType.TIME_CORRECTION, today);
        // Assigned to the supervisor; any TIME_ADMIN may also decide (AGENT.md §15.5).
        StepSpec step = new StepSpec("TIME_CORRECTION_APPROVAL", ApprovalType.TIME_CORRECTION,
                approvers.isEmpty() ? null : approvers.getFirst(), Role.TIME_ADMIN,
                "Approve time correction – " + name, describe(request) + ". Reason: " + request.getReason());
        request.attachWorkflow(workflow.start("TIME_CORRECTION", BUSINESS_OBJECT_TYPE, request.getId(), employeeId,
                List.of(step)));
        accounts.recalculateDay(employeeId, input.date());
        audit.record("TIME_CORRECTION_REQUESTED", BUSINESS_OBJECT_TYPE, request.getId(), null, snapshot(request));
        return request;
    }

    public TimeCorrectionRequest decide(UUID correctionId, Decision decision, String comment, OpsPrincipal actor) {
        TimeCorrectionRequest request = corrections.findById(correctionId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Time correction"));
        UUID taskId = workflow.openTaskOf(request.getWorkflowInstanceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE,
                        "This correction is not awaiting a decision."));
        workflow.decide(taskId, decision, comment, null, actor);
        return request;
    }

    @Transactional(readOnly = true)
    public List<TimeCorrectionRequest> mine(UUID employeeId) {
        return corrections.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public TimeCorrectionRequest visible(UUID id, OpsPrincipal principal) {
        TimeCorrectionRequest request = corrections.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Time correction"));
        boolean allowed = request.getEmployeeId().equals(principal.employeeId())
                || principal.hasRole(Role.TIME_ADMIN)
                || employees.isApproverOf(principal.employeeId(), request.getEmployeeId(),
                ApprovalType.TIME_CORRECTION, LocalDate.now(clock))
                || (request.getWorkflowInstanceId() != null && workflow.isInvolved(request.getWorkflowInstanceId(),
                principal));
        if (!allowed) {
            throw BusinessException.forbidden();
        }
        return request;
    }

    // ------------------------------------------------------- workflow reactions

    @EventListener
    void on(WorkflowEvents.Completed event) {
        if (!event.concerns(BUSINESS_OBJECT_TYPE)) {
            return;
        }
        TimeCorrectionRequest request = corrections.findById(event.businessObjectId()).orElseThrow();
        Instant now = Instant.now(clock);
        if (event.outcome() == InstanceStatus.APPROVED) {
            // Re-validate: the day's entries or the month's closing may have changed since the request.
            requireOpenMonth(request.getDate());
            requireValidResult(request);
            Map<String, Object> before = Map.of("entries", describeEntries(active(request)));
            apply(request, now);
            request.decide(true, now);
            accounts.recalculateDay(request.getEmployeeId(), request.getDate());
            audit.record("TIME_CORRECTION_APPLIED", BUSINESS_OBJECT_TYPE, request.getId(), before,
                    Map.of("entries", describeEntries(active(request))));
            notifications.notify(request.getEmployeeId(), NotificationType.TIME_CORRECTION_APPROVED,
                    BUSINESS_OBJECT_TYPE, request.getId(), "Time correction approved",
                    "Your time correction for " + request.getDate().format(DATE) + " was approved and applied.");
        } else {
            request.decide(false, now);
            accounts.recalculateDay(request.getEmployeeId(), request.getDate());
            audit.record("TIME_CORRECTION_REJECTED", BUSINESS_OBJECT_TYPE, request.getId(), null,
                    Map.of("outcome", event.outcome()));
            notifications.notify(request.getEmployeeId(), NotificationType.TIME_CORRECTION_REJECTED,
                    BUSINESS_OBJECT_TYPE, request.getId(), "Time correction rejected",
                    "Your time correction for " + request.getDate().format(DATE) + " was not approved."
                            + (event.comment() == null ? "" : " Reason: " + event.comment()));
        }
    }

    @EventListener
    void on(WorkflowEvents.TaskCreated event) {
        if (!event.businessObjectType().equals(BUSINESS_OBJECT_TYPE) || event.assignedEmployeeId() == null) {
            return;
        }
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(event.assignedEmployeeId());
        recipients.addAll(delegations.effectiveDelegatesOf(event.assignedEmployeeId(), event.approvalType(),
                LocalDate.now(clock)));
        recipients.forEach(r -> notifications.notify(r, NotificationType.TIME_CORRECTION_REQUIRED,
                BUSINESS_OBJECT_TYPE, event.businessObjectId(), "Time correction to approve", event.title()));
    }

    // ------------------------------------------------------------------ helpers

    private void requireOpenMonth(LocalDate date) {
        if (accounts.isClosed(date)) {
            throw new BusinessException(ErrorCode.TIME_MONTH_CLOSED,
                    "The time accounts for " + YearMonth.from(date)
                            + " are closed. Ask a time administrator to reopen the month.");
        }
    }

    private void apply(TimeCorrectionRequest r, Instant now) {
        if (r.getOperation() != Operation.ADD) {
            entries.findById(r.getOriginalTimeEntryId()).orElseThrow().voidBy(r.getId(), now);
        }
        if (r.getOperation() != Operation.DELETE) {
            entries.save(new TimeEntry(r.getEmployeeId(), r.getRequestedTimestamp(), r.getDate(),
                    r.getRequestedType(), TimeEntry.Source.ADMIN, now, r.getId()));
        }
    }

    /** Simulates the correction and rejects it if the resulting day would be an invalid sequence. */
    private void requireValidResult(TimeCorrectionRequest r) {
        List<Event> events = new ArrayList<>();
        for (TimeEntry e : active(r)) {
            if (r.getOperation() != Operation.ADD && e.getId().equals(r.getOriginalTimeEntryId())) {
                continue;
            }
            events.add(new Event(e.getTimestamp(), e.getType()));
        }
        if (r.getOperation() != Operation.DELETE) {
            events.add(new Event(r.getRequestedTimestamp(), r.getRequestedType()));
        }
        events.sort(Comparator.comparing(Event::timestamp));
        if (!TimeSequence.isValidDay(events)) {
            throw new BusinessException(ErrorCode.TIME_SEQUENCE_INVALID,
                    "After this correction the day's entries would not be in a valid order "
                            + "(clock in, breaks, clock out).");
        }
    }

    private List<TimeEntry> active(TimeCorrectionRequest r) {
        return entries.findByEmployeeIdAndBusinessDateAndVoidedAtIsNullOrderByTimestamp(r.getEmployeeId(),
                r.getDate());
    }

    private List<String> describeEntries(List<TimeEntry> list) {
        return list.stream().map(e -> e.getType() + "@" + time(e.getTimestamp())).toList();
    }

    private String time(Instant instant) {
        return instant.atZone(properties.timezone()).format(TIME);
    }

    private String describe(TimeCorrectionRequest r) {
        String what = switch (r.getOperation()) {
            case ADD -> "Add " + r.getRequestedType() + " at " + time(r.getRequestedTimestamp());
            case MODIFY -> "Change entry to " + r.getRequestedType() + " at " + time(r.getRequestedTimestamp());
            case DELETE -> "Remove an entry";
        };
        return what + " on " + r.getDate().format(DATE);
    }

    private Map<String, Object> snapshot(TimeCorrectionRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", r.getDate());
        m.put("operation", r.getOperation());
        if (r.getRequestedTimestamp() != null) {
            m.put("requested", r.getRequestedType() + "@" + time(r.getRequestedTimestamp()));
        }
        return m;
    }
}
