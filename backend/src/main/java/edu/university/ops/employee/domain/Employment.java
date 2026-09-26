package edu.university.ops.employee.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** One employment relationship; a person may hold several over time (AGENT.md §8). */
@Entity
@Table(name = "employment")
public class Employment {

    public enum Type { ACADEMIC, ADMINISTRATIVE, TECHNICAL, STUDENT_ASSISTANT, OTHER }

    public enum Status { ACTIVE, FUTURE, ENDED, SUSPENDED }

    @Id
    private UUID id;

    private UUID employeeId;
    private String externalEmploymentId;
    private LocalDate startDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    private Type employmentType;

    private BigDecimal weeklyHours;
    private BigDecimal fullTimeEquivalent;
    private UUID workScheduleId;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Version
    private Long version;

    protected Employment() {
    }

    public Employment(UUID employeeId, String externalEmploymentId) {
        this.id = UUID.randomUUID();
        this.employeeId = employeeId;
        this.externalEmploymentId = externalEmploymentId;
    }

    /** Applies contract data from the personnel ERP; returns true if anything changed. */
    public boolean applyContract(LocalDate start, LocalDate end, Type type, BigDecimal weeklyHours,
                                 BigDecimal fullTimeEquivalent, UUID workScheduleId, LocalDate today) {
        Status newStatus = end != null && end.isBefore(today) ? Status.ENDED
                : start.isAfter(today) ? Status.FUTURE : Status.ACTIVE;
        boolean changed = !start.equals(startDate) || !Objects.equals(end, endDate)
                || type != employmentType || weeklyHours.compareTo(nz(this.weeklyHours)) != 0
                || fullTimeEquivalent.compareTo(nz(this.fullTimeEquivalent)) != 0 || newStatus != status
                || !Objects.equals(workScheduleId, this.workScheduleId);
        this.startDate = start;
        this.endDate = end;
        this.employmentType = type;
        this.weeklyHours = weeklyHours;
        this.fullTimeEquivalent = fullTimeEquivalent;
        this.workScheduleId = workScheduleId;
        this.status = newStatus;
        return changed;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.valueOf(-1) : value;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
    }

    /** Covers the whole period, e.g. a requested absence (AGENT.md §13.7). */
    public boolean covers(LocalDate from, LocalDate to) {
        return covers(from) && covers(to);
    }

    public boolean isCurrent(LocalDate today) {
        return status == Status.ACTIVE && covers(today);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public String getExternalEmploymentId() {
        return externalEmploymentId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Type getEmploymentType() {
        return employmentType;
    }

    public BigDecimal getWeeklyHours() {
        return weeklyHours;
    }

    public BigDecimal getFullTimeEquivalent() {
        return fullTimeEquivalent;
    }

    public UUID getWorkScheduleId() {
        return workScheduleId;
    }

    public Status getStatus() {
        return status;
    }
}
