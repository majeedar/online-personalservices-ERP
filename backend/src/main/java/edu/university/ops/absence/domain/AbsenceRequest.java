package edu.university.ops.absence.domain;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** An absence request and its calculated days (AGENT.md §13.5). */
@Entity
@Table(name = "absence_request")
public class AbsenceRequest {

    @Id
    private UUID id;

    private UUID employeeId;
    private UUID leaveTypeId;
    private LocalDate startDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    private DayPart startDayPart = DayPart.FULL;

    @Enumerated(EnumType.STRING)
    private DayPart endDayPart = DayPart.FULL;

    private UUID representativeEmployeeId;
    private String comment;

    @Enumerated(EnumType.STRING)
    private AbsenceStatus status;

    private UUID workflowInstanceId;
    private Instant createdAt;
    private Instant submittedAt;
    private Instant updatedAt;
    private Instant anonymisedAt;

    @Version
    private Long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "absence_request_id", nullable = false)
    @OrderBy("date")
    private List<AbsenceDay> days = new ArrayList<>();

    protected AbsenceRequest() {
    }

    public static AbsenceRequest draft(UUID employeeId, UUID leaveTypeId, LocalDate start, LocalDate end,
                                       DayParts parts, UUID representativeId, String comment, Instant now) {
        requireValidRange(start, end);
        AbsenceRequest r = new AbsenceRequest();
        r.id = UUID.randomUUID();
        r.employeeId = employeeId;
        r.leaveTypeId = leaveTypeId;
        r.startDate = start;
        r.endDate = end;
        r.startDayPart = parts.start();
        r.endDayPart = parts.end();
        r.representativeEmployeeId = representativeId;
        r.comment = comment;
        r.status = AbsenceStatus.DRAFT;
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    public void edit(UUID leaveTypeId, LocalDate start, LocalDate end, DayParts parts, UUID representativeId,
                     String comment, Instant now) {
        if (status != AbsenceStatus.DRAFT) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE, "Only draft requests can be edited.");
        }
        requireValidRange(start, end);
        this.leaveTypeId = leaveTypeId;
        this.startDate = start;
        this.endDate = end;
        this.startDayPart = parts.start();
        this.endDayPart = parts.end();
        this.representativeEmployeeId = representativeId;
        this.comment = comment;
        this.updatedAt = now;
    }

    public static void requireValidRange(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE, "The end date must not be before the start date.");
        }
        if (start.plusDays(AbsenceDayCalculator.MAX_DAYS).isBefore(end)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE, "An absence request may span at most one year.");
        }
    }

    /**
     * Replaces the calculated days. Existing rows are updated in place (by date), so
     * the unique (request, date) constraint is never hit by delete-after-insert ordering.
     */
    public void replaceDays(List<AbsenceDayCalculator.CalculatedDay> calculated) {
        Map<LocalDate, AbsenceDay> existing = days.stream()
                .collect(Collectors.toMap(AbsenceDay::getDate, Function.identity()));
        List<AbsenceDay> result = new ArrayList<>();
        for (AbsenceDayCalculator.CalculatedDay c : calculated) {
            AbsenceDay day = existing.remove(c.date());
            if (day == null) {
                day = new AbsenceDay(c);
            } else {
                day.apply(c);
            }
            result.add(day);
        }
        days.removeIf(d -> existing.containsKey(d.getDate()));
        for (AbsenceDay d : result) {
            if (!days.contains(d)) {
                days.add(d);
            }
        }
        days.sort((a, b) -> a.getDate().compareTo(b.getDate()));
    }

    // ------------------------------------------------------------- lifecycle

    public void submit(Instant now) {
        transition(AbsenceStatus.SUBMITTED, now);
        this.submittedAt = now;
    }

    public void startApproval(UUID workflowInstanceId, Instant now) {
        transition(AbsenceStatus.IN_APPROVAL, now);
        this.workflowInstanceId = workflowInstanceId;
    }

    public void approve(Instant now) {
        transition(AbsenceStatus.APPROVED, now);
    }

    public void reject(Instant now) {
        transition(AbsenceStatus.REJECTED, now);
    }

    public void returnForCorrection(Instant now) {
        transition(AbsenceStatus.DRAFT, now);
    }

    public void cancel(Instant now) {
        transition(AbsenceStatus.CANCELLED, now);
    }

    public void requestCancellation(UUID workflowInstanceId, Instant now) {
        transition(AbsenceStatus.CANCEL_REQUESTED, now);
        this.workflowInstanceId = workflowInstanceId;
    }

    public void keepAfterRejectedCancellation(Instant now) {
        transition(AbsenceStatus.APPROVED, now);
    }

    private void transition(AbsenceStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE,
                    "A request in status " + status + " cannot become " + target + ".");
        }
        this.status = target;
        this.updatedAt = now;
    }

    /**
     * Retention (AGENT.md §82): removes personal free text and the representative.
     * Dates, days and status stay, because entitlements and time accounts rely on them.
     */
    public void anonymise(Instant now) {
        this.comment = null;
        this.representativeEmployeeId = null;
        this.anonymisedAt = now;
    }

    public Instant getAnonymisedAt() {
        return anonymisedAt;
    }

    // ---------------------------------------------------------------- queries

    public boolean overlaps(LocalDate from, LocalDate to) {
        return !startDate.isAfter(to) && !endDate.isBefore(from);
    }

    public BigDecimal totalDeduction() {
        return days.stream().map(AbsenceDay::getEntitlementDeduction).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Entitlement deduction per calendar year (a request may span New Year). */
    public Map<Integer, BigDecimal> deductionByYear() {
        Map<Integer, BigDecimal> result = new TreeMap<>();
        days.forEach(d -> result.merge(d.getDate().getYear(), d.getEntitlementDeduction(), BigDecimal::add));
        result.values().removeIf(v -> v.signum() == 0);
        return result;
    }

    /** Working days covered, counting a half day as 0.5. */
    public BigDecimal workingDays() {
        return days.stream().map(AbsenceDay::workingDayShare).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public DayParts getDayParts() {
        return new DayParts(startDayPart, endDayPart);
    }

    /** The part of {@code date} this request covers (full day between the first and last day). */
    public DayPart dayPartOn(LocalDate date) {
        return getDayParts().on(date, startDate, endDate);
    }

    public List<LocalDate> dates() {
        return days.stream().map(AbsenceDay::getDate).toList();
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getLeaveTypeId() {
        return leaveTypeId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public UUID getRepresentativeEmployeeId() {
        return representativeEmployeeId;
    }

    public String getComment() {
        return comment;
    }

    public AbsenceStatus getStatus() {
        return status;
    }

    public UUID getWorkflowInstanceId() {
        return workflowInstanceId;
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

    public List<AbsenceDay> getDays() {
        return List.copyOf(days);
    }
}
