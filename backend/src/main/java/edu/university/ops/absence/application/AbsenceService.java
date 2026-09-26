package edu.university.ops.absence.application;

import edu.university.ops.absence.AbsenceEvents;
import edu.university.ops.absence.application.AbsenceRules.Issue;
import edu.university.ops.absence.domain.AbsenceDayCalculator.CalculatedDay;
import edu.university.ops.absence.domain.AbsenceRepositories.AbsenceRequestRepository;
import edu.university.ops.absence.domain.AbsenceRepositories.LeaveEntitlementRepository;
import edu.university.ops.absence.domain.AbsenceRepositories.LeaveTypeRepository;
import edu.university.ops.absence.domain.AbsenceRequest;
import edu.university.ops.absence.domain.AbsenceStatus;
import edu.university.ops.absence.domain.DayPart;
import edu.university.ops.absence.domain.DayParts;
import edu.university.ops.absence.domain.LeaveEntitlement;
import edu.university.ops.absence.domain.LeaveType;
import edu.university.ops.employee.EmployeeDirectory;
import edu.university.ops.shared.audit.AuditService;
import edu.university.ops.shared.documents.DocumentService;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.shared.notification.NotificationService;
import edu.university.ops.shared.notification.NotificationType;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.security.Role;
import edu.university.ops.shared.workflow.ApprovalType;
import edu.university.ops.shared.workflow.StepSpec;
import edu.university.ops.shared.workflow.WorkflowEnums.Decision;
import edu.university.ops.shared.workflow.WorkflowService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Absence use cases: draft, submit, decide, cancel (AGENT.md §13.8). Request
 * status is owned here; the workflow engine reports decisions back through
 * {@link AbsenceWorkflowHandler} in the same transaction (ADR-004).
 */
@Service
@Transactional
public class AbsenceService {

    public static final String BUSINESS_OBJECT_TYPE = "AbsenceRequest";
    static final String APPROVAL_WORKFLOW = "ABSENCE_APPROVAL";
    static final String CANCELLATION_WORKFLOW = "ABSENCE_CANCELLATION";
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AbsenceRequestRepository requests;
    private final LeaveTypeRepository leaveTypes;
    private final LeaveEntitlementRepository entitlements;
    private final AbsenceRules rules;
    private final EmployeeDirectory employees;
    private final WorkflowService workflow;
    private final NotificationService notifications;
    private final DocumentService documents;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    AbsenceService(AbsenceRequestRepository requests, LeaveTypeRepository leaveTypes,
                   LeaveEntitlementRepository entitlements, AbsenceRules rules,
                   EmployeeDirectory employees, WorkflowService workflow, NotificationService notifications,
                   DocumentService documents, AuditService audit, ApplicationEventPublisher events, Clock clock) {
        this.requests = requests;
        this.leaveTypes = leaveTypes;
        this.entitlements = entitlements;
        this.rules = rules;
        this.employees = employees;
        this.workflow = workflow;
        this.notifications = notifications;
        this.documents = documents;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public record Preview(List<CalculatedDay> days, BigDecimal workingDays, BigDecimal deduction,
                          BigDecimal currentBalance, BigDecimal projectedBalance, List<Issue> issues) {
    }

    public record AbsenceInput(UUID leaveTypeId, LocalDate startDate, LocalDate endDate, DayPart startDayPart,
                               DayPart endDayPart, UUID representativeId, String comment) {

        public AbsenceInput(UUID leaveTypeId, LocalDate startDate, LocalDate endDate, UUID representativeId,
                            String comment) {
            this(leaveTypeId, startDate, endDate, null, null, representativeId, comment);
        }

        /** Validated day parts; throws INVALID_DAY_PART. */
        public DayParts dayParts() {
            return DayParts.of(startDate, endDate, startDayPart, endDayPart);
        }
    }

    // ------------------------------------------------------------------ preview

    @Transactional(readOnly = true)
    public Preview preview(OpsPrincipal principal, AbsenceInput input, UUID excludeRequestId) {
        LeaveType type = leaveType(input.leaveTypeId());
        DayParts parts = input.dayParts();
        List<CalculatedDay> days = rules.calculateDays(principal.employeeId(), type, input.startDate(),
                input.endDate(), parts);
        List<Issue> issues = rules.check(principal.employeeId(), type, input.startDate(), input.endDate(), parts,
                input.representativeId(), days, excludeRequestId).stream()
                .filter(i -> i.code() != ErrorCode.ATTACHMENT_REQUIRED || excludeRequestId == null
                        || !documents.hasDocuments(BUSINESS_OBJECT_TYPE, excludeRequestId))
                .toList();
        BigDecimal deduction = AbsenceRules.deductionByYear(days).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal current = null;
        BigDecimal projected = null;
        if (type.isDeductsEntitlement()) {
            int year = input.startDate().getYear();
            var ent = entitlements.findByEmployeeIdAndYearAndLeaveTypeId(principal.employeeId(), year, type.getId());
            if (ent.isPresent()) {
                current = ent.get().remainingDays();
                projected = current.subtract(AbsenceRules.deductionByYear(days).getOrDefault(year, BigDecimal.ZERO));
            }
        }
        BigDecimal working = days.stream().map(CalculatedDay::workingDayShare)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Preview(days, working, deduction, current, projected, issues);
    }

    // ------------------------------------------------------------------- drafts

    public AbsenceRequest createDraft(OpsPrincipal principal, AbsenceInput input) {
        LeaveType type = leaveType(input.leaveTypeId());
        Instant now = Instant.now(clock);
        DayParts parts = input.dayParts();
        AbsenceRequest request = AbsenceRequest.draft(principal.employeeId(), type.getId(), input.startDate(),
                input.endDate(), parts, input.representativeId(), input.comment(), now);
        request.replaceDays(rules.calculateDays(principal.employeeId(), type, input.startDate(), input.endDate(),
                parts));
        requests.save(request);
        audit.record("ABSENCE_DRAFT_CREATED", BUSINESS_OBJECT_TYPE, request.getId(), null, snapshot(request, type));
        return request;
    }

    public AbsenceRequest updateDraft(UUID requestId, OpsPrincipal principal, AbsenceInput input) {
        AbsenceRequest request = ownRequest(requestId, principal);
        LeaveType type = leaveType(input.leaveTypeId());
        var before = snapshot(request, leaveType(request.getLeaveTypeId()));
        DayParts parts = input.dayParts();
        request.edit(type.getId(), input.startDate(), input.endDate(), parts, input.representativeId(),
                input.comment(), Instant.now(clock));
        request.replaceDays(rules.calculateDays(request.getEmployeeId(), type, input.startDate(), input.endDate(),
                parts));
        audit.record("ABSENCE_DRAFT_UPDATED", BUSINESS_OBJECT_TYPE, request.getId(), before, snapshot(request, type));
        return request;
    }

    // ------------------------------------------------------------------- submit

    public AbsenceRequest submit(UUID requestId, OpsPrincipal principal) {
        AbsenceRequest request = ownRequest(requestId, principal);
        if (request.getStatus() != AbsenceStatus.DRAFT) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE, "Only draft requests can be submitted.");
        }
        LeaveType type = leaveType(request.getLeaveTypeId());
        UUID employeeId = request.getEmployeeId();

        // Recalculate: the schedule or holiday calendar may have changed since the draft was saved.
        List<CalculatedDay> days = rules.calculateDays(employeeId, type, request.getStartDate(), request.getEndDate(),
                request.getDayParts());
        List<Issue> issues = rules.check(employeeId, type, request.getStartDate(), request.getEndDate(),
                request.getDayParts(), request.getRepresentativeEmployeeId(), days, request.getId()).stream()
                .filter(i -> i.code() != ErrorCode.ATTACHMENT_REQUIRED
                        || !documents.hasDocuments(BUSINESS_OBJECT_TYPE, request.getId()))
                .toList();
        rules.enforce(issues);
        request.replaceDays(days);

        Instant now = Instant.now(clock);
        request.submit(now);
        String employeeName = name(employeeId);

        if (type.isRequiresApproval()) {
            UUID instanceId = workflow.start(APPROVAL_WORKFLOW, BUSINESS_OBJECT_TYPE, request.getId(), employeeId,
                    List.of(approvalStep(request, type, employeeName, "SUPERVISOR_APPROVAL",
                            "Approve " + type.getName().toLowerCase() + " – " + employeeName)));
            request.startApproval(instanceId, now);
            forEachYear(request, (ent, d) -> ent.reserve(d), type);
            notifications.notify(employeeId, NotificationType.ABSENCE_SUBMITTED, BUSINESS_OBJECT_TYPE, request.getId(),
                    "Absence request submitted",
                    "Your " + type.getName().toLowerCase() + " request for " + period(request)
                            + " was submitted for approval.");
        } else {
            // Types without approval (e.g. sick leave) take effect immediately.
            request.approve(now);
            forEachYear(request, (ent, d) -> ent.use(d), type);
            events.publishEvent(new AbsenceEvents.AbsenceApproved(request.getId(), employeeId, request.dates()));
            notifications.notify(employeeId, NotificationType.ABSENCE_APPROVED, BUSINESS_OBJECT_TYPE, request.getId(),
                    "Absence recorded", "Your " + type.getName().toLowerCase() + " for " + period(request)
                            + " has been recorded.");
        }
        events.publishEvent(new AbsenceEvents.AbsenceSubmitted(request.getId(), employeeId, request.getStartDate(),
                request.getEndDate()));
        audit.record("ABSENCE_SUBMITTED", BUSINESS_OBJECT_TYPE, request.getId(), Map.of("status", "DRAFT"),
                Map.of("status", request.getStatus(), "workingDays", request.workingDays(),
                        "deduction", request.totalDeduction()));
        return request;
    }

    // ------------------------------------------------------------- decisions

    /** Approve / reject / return via the request's open workflow task (AGENT.md §35). */
    public AbsenceRequest decide(UUID requestId, Decision decision, String comment, OpsPrincipal principal) {
        AbsenceRequest request = requests.findById(requestId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Absence request"));
        if (request.getWorkflowInstanceId() == null) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE, "This request is not awaiting a decision.");
        }
        UUID taskId = workflow.openTaskOf(request.getWorkflowInstanceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE,
                        "This request is not awaiting a decision."));
        workflow.decide(taskId, decision, comment, null, principal);
        return request;
    }

    // ------------------------------------------------------------------- cancel

    public AbsenceRequest cancel(UUID requestId, OpsPrincipal principal, String reason) {
        AbsenceRequest request = requests.findById(requestId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Absence request"));
        boolean owner = request.getEmployeeId().equals(principal.employeeId());
        if (!owner && !principal.hasRole(Role.HR_ADMIN)) {
            throw BusinessException.forbidden();
        }
        LeaveType type = leaveType(request.getLeaveTypeId());
        Instant now = Instant.now(clock);
        AbsenceStatus before = request.getStatus();

        switch (before) {
            case DRAFT -> request.cancel(now);
            case IN_APPROVAL -> {
                workflow.cancel(request.getWorkflowInstanceId());
                request.cancel(now);
                forEachYear(request, (ent, d) -> ent.releaseReservation(d), type);
            }
            case APPROVED -> {
                if (type.isRequiresApproval() && owner) {
                    String employeeName = name(request.getEmployeeId());
                    UUID instanceId = workflow.start(CANCELLATION_WORKFLOW, BUSINESS_OBJECT_TYPE, request.getId(),
                            request.getEmployeeId(), List.of(approvalStep(request, type, employeeName,
                                    "SUPERVISOR_APPROVAL",
                                    "Approve cancellation of " + type.getName().toLowerCase() + " – "
                                            + employeeName)));
                    request.requestCancellation(instanceId, now);
                } else {
                    // No approval needed (e.g. sick leave), or HR performs an administrative cancellation.
                    confirmCancellation(request, type, now);
                }
            }
            default -> throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE,
                    "A request in status " + before + " cannot be cancelled.");
        }
        audit.record(owner ? "ABSENCE_CANCELLATION" : "ABSENCE_ADMIN_CANCELLATION", BUSINESS_OBJECT_TYPE,
                request.getId(), Map.of("status", before),
                reason == null ? Map.of("status", request.getStatus())
                        : Map.of("status", request.getStatus(), "reasonGiven", true));
        return request;
    }

    // --------------------------------------------- reactions to workflow outcome

    void onApprovalCompleted(AbsenceRequest request, String outcome, String comment) {
        LeaveType type = leaveType(request.getLeaveTypeId());
        Instant now = Instant.now(clock);
        AbsenceStatus before = request.getStatus();
        switch (outcome) {
            case "APPROVED" -> {
                request.approve(now);
                forEachYear(request, (ent, d) -> ent.consumeReservation(d), type);
                events.publishEvent(new AbsenceEvents.AbsenceApproved(request.getId(), request.getEmployeeId(),
                        request.dates()));
                notifyEmployee(request, NotificationType.ABSENCE_APPROVED, "Absence approved",
                        "Your " + type.getName().toLowerCase() + " for " + period(request) + " has been approved.");
            }
            case "REJECTED" -> {
                request.reject(now);
                forEachYear(request, (ent, d) -> ent.releaseReservation(d), type);
                events.publishEvent(new AbsenceEvents.AbsenceRejected(request.getId(), request.getEmployeeId()));
                notifyEmployee(request, NotificationType.ABSENCE_REJECTED, "Absence rejected",
                        "Your " + type.getName().toLowerCase() + " for " + period(request) + " was rejected."
                                + reason(comment));
            }
            case "RETURNED" -> {
                request.returnForCorrection(now);
                forEachYear(request, (ent, d) -> ent.releaseReservation(d), type);
                notifyEmployee(request, NotificationType.ABSENCE_RETURNED, "Absence request returned",
                        "Your " + type.getName().toLowerCase() + " request for " + period(request)
                                + " was returned for correction." + reason(comment));
            }
            default -> {
                return;
            }
        }
        audit.record("ABSENCE_" + outcome, BUSINESS_OBJECT_TYPE, request.getId(), Map.of("status", before),
                Map.of("status", request.getStatus()));
    }

    void onCancellationCompleted(AbsenceRequest request, String outcome, String comment) {
        LeaveType type = leaveType(request.getLeaveTypeId());
        Instant now = Instant.now(clock);
        if ("APPROVED".equals(outcome)) {
            confirmCancellation(request, type, now);
            audit.record("ABSENCE_CANCELLED", BUSINESS_OBJECT_TYPE, request.getId(),
                    Map.of("status", AbsenceStatus.CANCEL_REQUESTED), Map.of("status", request.getStatus()));
        } else {
            request.keepAfterRejectedCancellation(now);
            notifyEmployee(request, NotificationType.ABSENCE_REJECTED, "Cancellation rejected",
                    "The cancellation of your absence " + period(request) + " was not approved; the absence stands."
                            + reason(comment));
            audit.record("ABSENCE_CANCELLATION_REJECTED", BUSINESS_OBJECT_TYPE, request.getId(),
                    Map.of("status", AbsenceStatus.CANCEL_REQUESTED), Map.of("status", request.getStatus()));
        }
    }

    // ---------------------------------------------------------------- helpers

    private void confirmCancellation(AbsenceRequest request, LeaveType type, Instant now) {
        request.cancel(now);
        forEachYear(request, (ent, d) -> ent.restore(d), type);
        events.publishEvent(new AbsenceEvents.AbsenceCancelled(request.getId(), request.getEmployeeId(),
                request.dates()));
        notifyEmployee(request, NotificationType.ABSENCE_CANCELLED, "Absence cancelled",
                "Your absence " + period(request) + " has been cancelled.");
    }

    private StepSpec approvalStep(AbsenceRequest request, LeaveType type, String employeeName, String stepType,
                                  String title) {
        String description = employeeName + ": " + type.getName() + ", " + period(request) + " ("
                + request.workingDays().stripTrailingZeros().toPlainString() + " working day(s))";
        List<UUID> approvers = employees.approversOf(request.getEmployeeId(), ApprovalType.ABSENCE,
                LocalDate.now(clock));
        // No configured approver: HR handles the request instead of it getting stuck.
        return approvers.isEmpty()
                ? StepSpec.toRole(stepType, ApprovalType.ABSENCE, Role.HR_ADMIN, title, description)
                : StepSpec.toPerson(stepType, ApprovalType.ABSENCE, approvers.getFirst(), title, description);
    }

    /** Applies an entitlement change for each year the request touches (deducting types only). */
    private void forEachYear(AbsenceRequest request, BiConsumer<LeaveEntitlement, BigDecimal> change,
                             LeaveType type) {
        if (!type.isDeductsEntitlement()) {
            return;
        }
        request.deductionByYear().forEach((year, days) -> entitlements
                .findByEmployeeIdAndYearAndLeaveTypeId(request.getEmployeeId(), year, type.getId())
                .ifPresent(ent -> change.accept(ent, days)));
    }

    private AbsenceRequest ownRequest(UUID requestId, OpsPrincipal principal) {
        AbsenceRequest request = requests.findById(requestId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Absence request"));
        if (!request.getEmployeeId().equals(principal.employeeId())) {
            throw BusinessException.forbidden();
        }
        return request;
    }

    LeaveType leaveType(UUID id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose a leave type.");
        }
        return leaveTypes.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Leave type"));
    }

    private void notifyEmployee(AbsenceRequest request, NotificationType type, String subject, String message) {
        notifications.notify(request.getEmployeeId(), type, BUSINESS_OBJECT_TYPE, request.getId(), subject, message);
    }

    private String name(UUID employeeId) {
        return employees.findEmployee(employeeId).map(EmployeeDirectory.EmployeeSummary::displayName)
                .orElse("Unknown");
    }

    static String period(AbsenceRequest r) {
        DayParts parts = r.getDayParts();
        String start = r.getStartDate().format(DATE) + half(parts.start());
        return r.getStartDate().equals(r.getEndDate()) ? start
                : start + " – " + r.getEndDate().format(DATE) + half(parts.end());
    }

    private static String half(DayPart part) {
        return part.isHalf() ? " (" + part.name().toLowerCase() + ")" : "";
    }

    private static String reason(String comment) {
        return comment == null ? "" : " Reason: " + comment;
    }

    private static Map<String, Object> snapshot(AbsenceRequest r, LeaveType type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("leaveType", type.getCode());
        m.put("startDate", r.getStartDate());
        m.put("endDate", r.getEndDate());
        if (!r.getDayParts().isFull()) {
            m.put("dayParts", r.getDayParts().start() + "/" + r.getDayParts().end());
        }
        m.put("status", r.getStatus());
        return m;
    }
}
