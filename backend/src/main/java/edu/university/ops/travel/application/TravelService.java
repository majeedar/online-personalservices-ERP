package edu.university.ops.travel.application;

import edu.university.ops.shared.i18n.Text;
import edu.university.ops.employee.EmployeeDirectory;
import edu.university.ops.shared.audit.AuditService;
import edu.university.ops.shared.documents.Document;
import edu.university.ops.shared.documents.DocumentService;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.shared.integration.IntegrationCalls;
import edu.university.ops.shared.integration.OutboxService;
import edu.university.ops.shared.notification.NotificationService;
import edu.university.ops.shared.notification.NotificationType;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.security.Role;
import edu.university.ops.shared.workflow.ApprovalType;
import edu.university.ops.shared.workflow.StepSpec;
import edu.university.ops.shared.workflow.WorkflowEnums.Decision;
import edu.university.ops.shared.workflow.WorkflowService;
import edu.university.ops.shared.workflow.WorkflowViews.TaskView;
import edu.university.ops.travel.TravelEvents;
import edu.university.ops.travel.domain.FundingRules;
import edu.university.ops.travel.domain.FundingSource;
import edu.university.ops.travel.domain.TravelExpense;
import edu.university.ops.travel.domain.TravelFunding;
import edu.university.ops.travel.domain.TravelPorts.ExportTypes;
import edu.university.ops.travel.domain.TravelPorts.FinanceGateway;
import edu.university.ops.travel.domain.TravelPorts.FundingSourceRepository;
import edu.university.ops.travel.domain.TravelPorts.TravelRequestRepository;
import edu.university.ops.travel.domain.TravelRequest;
import edu.university.ops.travel.domain.TravelStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Travel use cases (AGENT.md §14.2): request → supervisor approval → financial
 * approval → authorized (exported to the travel ERP) → completed → expense claim →
 * travel-office review → settled (settlement exported, finance posting created).
 */
@Service
@Transactional
public class TravelService {

    public static final String BUSINESS_OBJECT_TYPE = "TravelRequest";
    static final String APPROVAL_WORKFLOW = "TRAVEL_APPROVAL";
    static final String EXPENSE_WORKFLOW = "TRAVEL_EXPENSE_REVIEW";
    static final String STEP_SUPERVISOR = "SUPERVISOR_APPROVAL";
    static final String STEP_FINANCIAL = "FINANCIAL_APPROVAL";
    static final String STEP_TRAVEL_OFFICE = "TRAVEL_OFFICE_REVIEW";
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public record FundingInput(UUID fundingSourceId, BigDecimal percentage, BigDecimal amount) {
    }

    public record ExpenseInput(TravelExpense.Type type, LocalDate date, BigDecimal amount, String currency,
                               String description) {
    }

    private final TravelRequestRepository requests;
    private final FundingSourceRepository fundingSources;
    private final FinanceGateway finance;
    private final IntegrationCalls integrationCalls;
    private final OutboxService outbox;
    private final EmployeeDirectory employees;
    private final WorkflowService workflow;
    private final NotificationService notifications;
    private final DocumentService documents;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final TravelProperties properties;
    private final Clock clock;

    TravelService(TravelRequestRepository requests, FundingSourceRepository fundingSources, FinanceGateway finance,
                  IntegrationCalls integrationCalls, OutboxService outbox, EmployeeDirectory employees,
                  WorkflowService workflow, NotificationService notifications, DocumentService documents,
                  AuditService audit, ApplicationEventPublisher events, TravelProperties properties, Clock clock) {
        this.requests = requests;
        this.fundingSources = fundingSources;
        this.finance = finance;
        this.integrationCalls = integrationCalls;
        this.outbox = outbox;
        this.employees = employees;
        this.workflow = workflow;
        this.notifications = notifications;
        this.documents = documents;
        this.audit = audit;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ drafts

    public TravelRequest createDraft(OpsPrincipal principal, TravelRequest.Details details,
                                     List<FundingInput> fundings) {
        TravelRequest request = TravelRequest.draft(principal.employeeId(), details, toFundings(fundings),
                Instant.now(clock));
        requests.save(request);
        audit.record("TRAVEL_DRAFT_CREATED", BUSINESS_OBJECT_TYPE, request.getId(), null, snapshot(request));
        return request;
    }

    public TravelRequest updateDraft(UUID id, OpsPrincipal principal, TravelRequest.Details details,
                                     List<FundingInput> fundings) {
        TravelRequest request = own(id, principal);
        var before = snapshot(request);
        request.edit(details, toFundings(fundings), Instant.now(clock));
        audit.record("TRAVEL_DRAFT_UPDATED", BUSINESS_OBJECT_TYPE, id, before, snapshot(request));
        return request;
    }

    // ------------------------------------------------------------------ submit

    public TravelRequest submit(UUID id, OpsPrincipal principal) {
        TravelRequest request = own(id, principal);
        if (request.getStatus() != TravelStatus.DRAFT) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE, "Only draft requests can be submitted.");
        }
        validateForSubmission(request);

        String name = name(request.getEmployeeId());
        LocalDate today = LocalDate.now(clock);
        Text summary = Text.of("{name}: {purpose}, {city} ({period}), {cost} {currency}", "name", name, "purpose",
                request.getPurpose(), "city", request.getDestinationCity(), "period", period(request), "cost",
                request.getEstimatedCost(), "currency", request.getCurrency());
        List<StepSpec> steps = new ArrayList<>();
        steps.add(assignedStep(request, ApprovalType.TRAVEL, Role.HR_ADMIN, STEP_SUPERVISOR,
                Text.of("Approve business travel – {name}", "name", name), summary, today));
        if (request.getEstimatedCost().compareTo(properties.financialApprovalThreshold()) > 0) {
            steps.add(assignedStep(request, ApprovalType.FINANCIAL, Role.FINANCIAL_APPROVER, STEP_FINANCIAL,
                    Text.of("Perform financial approval – {name}", "name", name),
                    Text.of("{summary}, cost centre {costCentre}", "summary", summary, "costCentre",
                            request.getCostCentre()), today));
        }
        UUID instanceId = workflow.start(APPROVAL_WORKFLOW, BUSINESS_OBJECT_TYPE, id, request.getEmployeeId(), steps);
        request.submit(instanceId, Instant.now(clock));
        notifications.notify(request.getEmployeeId(), NotificationType.TRAVEL_SUBMITTED, BUSINESS_OBJECT_TYPE, id,
                Text.of("Travel request submitted"), Text.of("Your travel request to {city} was submitted for "
                        + "approval.", "city", request.getDestinationCity()));
        events.publishEvent(new TravelEvents.TravelSubmitted(id, request.getEmployeeId()));
        audit.record("TRAVEL_SUBMITTED", BUSINESS_OBJECT_TYPE, id, Map.of("status", TravelStatus.DRAFT),
                Map.of("status", request.getStatus(), "steps", steps.size()));
        return request;
    }

    /** Runs every rule of AGENT.md §14 that needs the database or the finance system. */
    void validateForSubmission(TravelRequest r) {
        Instant now = Instant.now(clock);
        if (r.getStartDateTime().isBefore(now.minus(Duration.ofDays(properties.maxDaysInPast())))) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE,
                    Text.of("Trips can be requested at most {days} days after they started.", "days",
                            properties.maxDaysInPast()));
        }
        if (!properties.currencies().contains(r.getCurrency())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    Text.of("Currency {currency} is not supported.", "currency", r.getCurrency()));
        }
        Map<UUID, FundingSource> sources = fundingSources
                .findByIdIn(r.getFundings().stream().map(TravelFunding::getFundingSourceId).toList()).stream()
                .collect(Collectors.toMap(FundingSource::getId, Function.identity()));
        List<String> fundingProblems = FundingRules.violations(r.getFundings(), sources, r.getEstimatedCost(),
                properties.limitFundingToEstimate());
        if (!fundingProblems.isEmpty()) {
            throw new BusinessException(ErrorCode.FUNDING_INVALID, fundingProblems.getFirst());
        }
        // Demo Scenario 3: the cost centre is checked in the finance system of record.
        boolean valid = integrationCalls.call("FINANCE_COST_CENTRE_VALIDATION", r.getCostCentre(),
                () -> finance.validateCostCentre(r.getCostCentre()));
        if (!valid) {
            throw new BusinessException(ErrorCode.COST_CENTRE_INVALID,
                    Text.of("Cost centre {costCentre} is not valid in the finance system.", "costCentre",
                            r.getCostCentre()));
        }
    }

    // --------------------------------------------------------------- decisions

    /**
     * Decides the open task of the trip. {@code expectedStep} guards endpoint
     * semantics (e.g. financial-approve only on the financial step); null = any step.
     */
    public TravelRequest decide(UUID id, Decision decision, String comment, String expectedStep,
                                OpsPrincipal principal) {
        TravelRequest request = requests.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Travel request"));
        if (request.getWorkflowInstanceId() == null) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE, "This trip is not awaiting a decision.");
        }
        TaskView task = workflow.actionableTask(request.getWorkflowInstanceId(), principal)
                .orElseThrow(() -> workflow.openTaskOf(request.getWorkflowInstanceId()).isPresent()
                        ? BusinessException.forbidden()
                        : new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE,
                        "This trip is not awaiting a decision."));
        if (expectedStep != null && !expectedStep.equals(task.stepType())) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE,
                    Text.of("The trip is at step {step}, not {expected}.", "step", Text.of(task.stepType()),
                            "expected", Text.of(expectedStep)));
        }
        workflow.decide(task.id(), decision, comment, null, principal);
        return request;
    }

    public TravelRequest cancel(UUID id, OpsPrincipal principal) {
        TravelRequest request = own(id, principal);
        TravelStatus before = request.getStatus();
        if (before == TravelStatus.IN_APPROVAL) {
            workflow.cancel(request.getWorkflowInstanceId());
        }
        if (before == TravelStatus.AUTHORIZED && !request.getStartDateTime().isAfter(Instant.now(clock))) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE, "A trip that has started cannot be cancelled.");
        }
        request.cancel(Instant.now(clock));
        audit.record("TRAVEL_CANCELLED", BUSINESS_OBJECT_TYPE, id, Map.of("status", before),
                Map.of("status", request.getStatus()));
        return request;
    }

    // ------------------------------------------------------- completion, expenses

    public TravelRequest markCompleted(UUID id, OpsPrincipal principal) {
        TravelRequest request = own(id, principal);
        request.markCompleted(Instant.now(clock));
        audit.record("TRAVEL_COMPLETED", BUSINESS_OBJECT_TYPE, id, Map.of("status", TravelStatus.AUTHORIZED),
                Map.of("status", request.getStatus()));
        return request;
    }

    public TravelExpense addExpense(UUID id, OpsPrincipal principal, ExpenseInput input) {
        TravelRequest request = own(id, principal);
        LocalDate first = LocalDate.ofInstant(request.getStartDateTime(), ZoneOffset.UTC).minusDays(1);
        LocalDate last = LocalDate.ofInstant(request.getEndDateTime(), ZoneOffset.UTC).plusDays(1);
        if (input.date() == null || input.date().isBefore(first) || input.date().isAfter(last)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The expense date must be within the trip.");
        }
        if (input.amount() == null || input.amount().signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The amount must be positive.");
        }
        TravelExpense expense = request.addExpense(new TravelExpense(input.type(), input.date(), input.amount(),
                input.currency() == null ? request.getCurrency() : input.currency().toUpperCase(),
                input.description()), Instant.now(clock));
        audit.record("TRAVEL_EXPENSE_ADDED", BUSINESS_OBJECT_TYPE, id, null,
                Map.of("expenseId", expense.getId(), "type", expense.getExpenseType(), "amount", expense.getAmount()));
        return expense;
    }

    public void removeExpense(UUID id, UUID expenseId, OpsPrincipal principal) {
        own(id, principal).removeExpense(expenseId, Instant.now(clock));
        audit.record("TRAVEL_EXPENSE_REMOVED", BUSINESS_OBJECT_TYPE, id, Map.of("expenseId", expenseId), null);
    }

    public Document attachReceipt(UUID id, UUID expenseId, MultipartFile file, OpsPrincipal principal) {
        TravelRequest request = own(id, principal);
        request.expense(expenseId);
        Document doc = documents.attach(BUSINESS_OBJECT_TYPE, id, "RECEIPT", file, principal.employeeId());
        request.attachReceipt(expenseId, doc.getId());
        return doc;
    }

    public TravelRequest submitExpenses(UUID id, OpsPrincipal principal) {
        TravelRequest request = own(id, principal);
        List<Text> missing = request.getExpenses().stream()
                .filter(e -> properties.receiptRequiredTypes().contains(e.getExpenseType().name())
                        && e.getReceiptDocumentId() == null)
                .map(e -> Text.of(e.getExpenseType().name())).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.ATTACHMENT_REQUIRED,
                    Text.of("Receipts are missing for: {types}.", "types", Text.list(missing)));
        }
        String name = name(request.getEmployeeId());
        UUID instanceId = workflow.start(EXPENSE_WORKFLOW, BUSINESS_OBJECT_TYPE, id, request.getEmployeeId(),
                List.of(StepSpec.toRole(STEP_TRAVEL_OFFICE, ApprovalType.TRAVEL, Role.TRAVEL_OFFICE,
                        Text.of("Review travel expense claim – {name}", "name", name),
                        Text.of("{name}: {city} ({period}), claimed {amount} {currency}", "name", name, "city",
                                request.getDestinationCity(), "period", period(request), "amount",
                                request.totalExpenses(), "currency", request.getCurrency()))));
        request.submitExpenses(instanceId, Instant.now(clock));
        audit.record("TRAVEL_EXPENSES_SUBMITTED", BUSINESS_OBJECT_TYPE, id, Map.of("status", TravelStatus.COMPLETED),
                Map.of("status", request.getStatus(), "total", request.totalExpenses()));
        return request;
    }

    // ---------------------------------------------- reactions to workflow outcome

    void onApprovalCompleted(TravelRequest request, String outcome, String comment) {
        Instant now = Instant.now(clock);
        TravelStatus before = request.getStatus();
        switch (outcome) {
            case "APPROVED" -> {
                request.authorize(now);
                outbox.enqueue(ExportTypes.TRAVEL_EXPORT, BUSINESS_OBJECT_TYPE, request.getId(),
                        "travel-export-" + request.getId(), Map.of("travelRequestId", request.getId()));
                events.publishEvent(new TravelEvents.TravelAuthorized(request.getId(), request.getEmployeeId()));
                notifyEmployee(request, NotificationType.TRAVEL_AUTHORIZED, Text.of("Business travel authorized"),
                        Text.of("Your trip to {city} ({period}) is authorized.", "city", request.getDestinationCity(),
                                "period", period(request)));
            }
            case "REJECTED" -> {
                request.reject(now);
                notifyEmployee(request, NotificationType.TRAVEL_REJECTED, Text.of("Business travel rejected"),
                        Text.of("Your trip to {city} was rejected.{reason}", "city", request.getDestinationCity(),
                                "reason", reason(comment)));
            }
            case "RETURNED" -> {
                request.returnForCorrection(now);
                notifyEmployee(request, NotificationType.TRAVEL_RETURNED, Text.of("Travel request returned"),
                        Text.of("Your trip to {city} was returned for correction.{reason}", "city",
                                request.getDestinationCity(), "reason", reason(comment)));
            }
            default -> {
                return;
            }
        }
        audit.record("TRAVEL_" + outcome, BUSINESS_OBJECT_TYPE, request.getId(), Map.of("status", before),
                Map.of("status", request.getStatus()));
    }

    void onExpenseReviewCompleted(TravelRequest request, String outcome, String comment) {
        Instant now = Instant.now(clock);
        if ("APPROVED".equals(outcome)) {
            BigDecimal amount = request.settle(now);
            // Two exports, each idempotent by key (AGENT.md §29): never a duplicate posting.
            outbox.enqueue(ExportTypes.SETTLEMENT_EXPORT, BUSINESS_OBJECT_TYPE, request.getId(),
                    "travel-settlement-" + request.getId(), Map.of("amount", amount));
            outbox.enqueue(ExportTypes.FINANCE_POSTING, BUSINESS_OBJECT_TYPE, request.getId(),
                    "finance-posting-" + request.getId(), Map.of("amount", amount));
            events.publishEvent(new TravelEvents.TravelSettlementCreated(request.getId(), request.getEmployeeId(),
                    amount, request.getCurrency()));
            notifyEmployee(request, NotificationType.TRAVEL_SETTLED, Text.of("Travel expenses settled"),
                    Text.of("Your expenses for the trip to {city} were settled: {amount} {currency}.", "city",
                            request.getDestinationCity(), "amount", amount, "currency", request.getCurrency()));
            audit.record("TRAVEL_SETTLED", BUSINESS_OBJECT_TYPE, request.getId(),
                    Map.of("status", TravelStatus.EXPENSES_SUBMITTED),
                    Map.of("status", request.getStatus(), "amount", amount));
        } else {
            request.returnExpenses(now);
            notifyEmployee(request, NotificationType.TRAVEL_RETURNED, Text.of("Expense claim returned"),
                    Text.of("Your expense claim for {city} needs correction.{reason}", "city",
                            request.getDestinationCity(), "reason", reason(comment)));
            audit.record("TRAVEL_EXPENSES_RETURNED", BUSINESS_OBJECT_TYPE, request.getId(),
                    Map.of("status", TravelStatus.EXPENSES_SUBMITTED), Map.of("status", request.getStatus()));
        }
    }

    // ------------------------------------------------------------------ helpers

    private StepSpec assignedStep(TravelRequest r, ApprovalType type, Role fallbackRole, String stepType,
                                  Text title, Text description, LocalDate today) {
        List<UUID> approvers = employees.approversOf(r.getEmployeeId(), type, today).stream()
                .filter(a -> !a.equals(r.getEmployeeId())).toList();
        return approvers.isEmpty() ? StepSpec.toRole(stepType, type, fallbackRole, title, description)
                : StepSpec.toPerson(stepType, type, approvers.getFirst(), title, description);
    }

    private List<TravelFunding> toFundings(List<FundingInput> inputs) {
        return inputs == null ? List.of() : inputs.stream()
                .map(f -> new TravelFunding(f.fundingSourceId(), f.percentage(), f.amount())).toList();
    }

    private TravelRequest own(UUID id, OpsPrincipal principal) {
        TravelRequest request = requests.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Travel request"));
        if (!request.getEmployeeId().equals(principal.employeeId())) {
            throw BusinessException.forbidden();
        }
        return request;
    }

    private void notifyEmployee(TravelRequest r, NotificationType type, Text subject, Text message) {
        notifications.notify(r.getEmployeeId(), type, BUSINESS_OBJECT_TYPE, r.getId(), subject, message);
    }

    private String name(UUID employeeId) {
        return employees.findEmployee(employeeId).map(EmployeeDirectory.EmployeeSummary::displayName)
                .orElse("Unknown");
    }

    static String period(TravelRequest r) {
        return DATE.format(r.getStartDateTime().atOffset(ZoneOffset.UTC)) + " – "
                + DATE.format(r.getEndDateTime().atOffset(ZoneOffset.UTC));
    }

    private static Text reason(String comment) {
        return comment == null ? Text.of("") : Text.of(" Reason: {comment}", "comment", comment);
    }

    private static Map<String, Object> snapshot(TravelRequest r) {
        return Map.of("destination", r.getDestinationCity(), "start", r.getStartDateTime(), "end",
                r.getEndDateTime(), "estimatedCost", r.getEstimatedCost(), "costCentre", r.getCostCentre(),
                "status", r.getStatus());
    }
}
