package edu.university.ops.travel.domain;

import edu.university.ops.shared.i18n.Text;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A business trip from request to settlement (AGENT.md §14.1). */
@Entity
@Table(name = "travel_request")
public class TravelRequest {

    public enum TransportMode { TRAIN, PUBLIC_TRANSPORT, CAR, FLIGHT, BICYCLE, OTHER }

    /** Editable trip details. */
    public record Details(String purpose, String destinationCity, String destinationCountry, Instant start,
                          Instant end, TransportMode transportMode, BigDecimal estimatedCost, String currency,
                          String costCentre, String projectCode, String comment) {
    }

    @Id
    private UUID id;

    private UUID employeeId;
    private String purpose;
    private String destinationCity;
    private String destinationCountry;
    private Instant startDateTime;
    private Instant endDateTime;

    @Enumerated(EnumType.STRING)
    private TransportMode transportMode;

    private BigDecimal estimatedCost;
    private String currency;
    private String costCentre;
    private String projectCode;
    private String comment;

    @Enumerated(EnumType.STRING)
    private TravelStatus status;

    private UUID workflowInstanceId;
    private String externalTravelReference;
    private BigDecimal settledAmount;
    private String settlementReference;
    private String financePostingReference;
    private Instant createdAt;
    private Instant submittedAt;
    private Instant updatedAt;
    private Instant anonymisedAt;

    @Version
    private Long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "travel_request_id", nullable = false)
    private List<TravelFunding> fundings = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "travel_request_id", nullable = false)
    @OrderBy("expenseDate")
    private List<TravelExpense> expenses = new ArrayList<>();

    protected TravelRequest() {
    }

    public static TravelRequest draft(UUID employeeId, Details details, List<TravelFunding> fundings, Instant now) {
        TravelRequest r = new TravelRequest();
        r.id = UUID.randomUUID();
        r.employeeId = employeeId;
        r.status = TravelStatus.DRAFT;
        r.createdAt = now;
        r.apply(details, fundings, now);
        return r;
    }

    public void edit(Details details, List<TravelFunding> newFundings, Instant now) {
        require(TravelStatus.DRAFT, "Only draft requests can be edited.");
        apply(details, newFundings, now);
    }

    private void apply(Details d, List<TravelFunding> newFundings, Instant now) {
        if (d.start() == null || d.end() == null || !d.end().isAfter(d.start())) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE, "The trip must end after it starts.");
        }
        this.purpose = d.purpose();
        this.destinationCity = d.destinationCity();
        this.destinationCountry = d.destinationCountry() == null ? null : d.destinationCountry().toUpperCase();
        this.startDateTime = d.start();
        this.endDateTime = d.end();
        this.transportMode = d.transportMode();
        this.estimatedCost = d.estimatedCost();
        this.currency = d.currency() == null ? null : d.currency().toUpperCase();
        this.costCentre = d.costCentre();
        this.projectCode = d.projectCode();
        this.comment = d.comment();
        this.fundings.clear();
        this.fundings.addAll(newFundings);
        this.updatedAt = now;
    }

    // --------------------------------------------------------------- lifecycle

    public void submit(UUID workflowInstanceId, Instant now) {
        transition(TravelStatus.IN_APPROVAL, now);
        this.workflowInstanceId = workflowInstanceId;
        this.submittedAt = now;
    }

    public void authorize(Instant now) {
        transition(TravelStatus.AUTHORIZED, now);
    }

    public void reject(Instant now) {
        transition(TravelStatus.REJECTED, now);
    }

    public void returnForCorrection(Instant now) {
        transition(TravelStatus.DRAFT, now);
    }

    public void cancel(Instant now) {
        transition(TravelStatus.CANCELLED, now);
    }

    public void markCompleted(Instant now) {
        if (startDateTime.isAfter(now)) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE, "A trip can be completed once it has started.");
        }
        transition(TravelStatus.COMPLETED, now);
    }

    public void submitExpenses(UUID reviewWorkflowId, Instant now) {
        if (expenses.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Add at least one expense before submitting.");
        }
        transition(TravelStatus.EXPENSES_SUBMITTED, now);
        this.workflowInstanceId = reviewWorkflowId;
        expenses.forEach(TravelExpense::submit);
    }

    public void returnExpenses(Instant now) {
        transition(TravelStatus.COMPLETED, now);
        expenses.forEach(TravelExpense::reopen);
    }

    public BigDecimal settle(Instant now) {
        transition(TravelStatus.SETTLED, now);
        expenses.forEach(TravelExpense::accept);
        this.settledAmount = totalExpenses();
        return settledAmount;
    }

    /**
     * Retention (AGENT.md §82): removes the comment, expense descriptions and receipt
     * links. Purpose, destination, dates, amounts and ERP references are accounting
     * records and stay.
     */
    public void anonymise(Instant now) {
        this.comment = null;
        this.expenses.forEach(TravelExpense::anonymise);
        this.anonymisedAt = now;
    }

    public Instant getAnonymisedAt() {
        return anonymisedAt;
    }

    public void recordTravelExport(String reference) {
        this.externalTravelReference = reference;
    }

    public void recordSettlementExport(String reference) {
        this.settlementReference = reference;
    }

    public void recordFinancePosting(String reference) {
        this.financePostingReference = reference;
    }

    // ----------------------------------------------------------------- expenses

    public TravelExpense addExpense(TravelExpense expense, Instant now) {
        require(TravelStatus.COMPLETED, "Expenses can be entered after the trip is marked completed.");
        if (!expense.getCurrency().equals(currency)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    Text.of("Expenses must be in the trip currency ({currency}).", "currency", currency));
        }
        expenses.add(expense);
        this.updatedAt = now;
        return expense;
    }

    public void removeExpense(UUID expenseId, Instant now) {
        require(TravelStatus.COMPLETED, "Expenses can only be changed before they are submitted.");
        if (!expenses.removeIf(e -> e.getId().equals(expenseId))) {
            throw BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Expense");
        }
        this.updatedAt = now;
    }

    public TravelExpense expense(UUID expenseId) {
        return expenses.stream().filter(e -> e.getId().equals(expenseId)).findFirst()
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Expense"));
    }

    public void attachReceipt(UUID expenseId, UUID documentId) {
        require(TravelStatus.COMPLETED, "Receipts can only be added before the expenses are submitted.");
        expense(expenseId).attachReceipt(documentId);
    }

    public BigDecimal totalExpenses() {
        return expenses.stream().map(TravelExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void require(TravelStatus expected, String message) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE, message);
        }
    }

    private void transition(TravelStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE,
                    Text.of("A trip in status {status} cannot become {target}.", "status", Text.of(status.name()),
                            "target", Text.of(target.name())));
        }
        this.status = target;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getDestinationCity() {
        return destinationCity;
    }

    public String getDestinationCountry() {
        return destinationCountry;
    }

    public Instant getStartDateTime() {
        return startDateTime;
    }

    public Instant getEndDateTime() {
        return endDateTime;
    }

    public TransportMode getTransportMode() {
        return transportMode;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCostCentre() {
        return costCentre;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getComment() {
        return comment;
    }

    public TravelStatus getStatus() {
        return status;
    }

    public UUID getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public String getExternalTravelReference() {
        return externalTravelReference;
    }

    public BigDecimal getSettledAmount() {
        return settledAmount;
    }

    public String getSettlementReference() {
        return settlementReference;
    }

    public String getFinancePostingReference() {
        return financePostingReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<TravelFunding> getFundings() {
        return List.copyOf(fundings);
    }

    public List<TravelExpense> getExpenses() {
        return List.copyOf(expenses);
    }
}
