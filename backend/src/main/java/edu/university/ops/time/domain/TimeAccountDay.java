package edu.university.ops.time.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Stored daily account (AGENT.md §15.2); recalculated whenever an input changes. */
@Entity
@Table(name = "time_account_day")
public class TimeAccountDay {

    public enum Status { OPEN, CALCULATED, CORRECTION_PENDING, CLOSED }

    @Id
    private UUID id;

    private UUID employeeId;
    private LocalDate date;
    private int targetMinutes;
    private int workedMinutes;
    private int breakMinutes;
    private int absenceMinutes;
    private int creditedMinutes;
    private int balanceMinutes;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Instant calculatedAt;

    @Version
    private Long version;

    protected TimeAccountDay() {
    }

    public TimeAccountDay(UUID employeeId, LocalDate date) {
        this.id = UUID.randomUUID();
        this.employeeId = employeeId;
        this.date = date;
        this.status = Status.OPEN;
    }

    public void update(int target, int worked, int breaks, int absence, int credited, int balance, Status status,
                       Instant now) {
        this.targetMinutes = target;
        this.workedMinutes = worked;
        this.breakMinutes = breaks;
        this.absenceMinutes = absence;
        this.creditedMinutes = credited;
        this.balanceMinutes = balance;
        this.status = status;
        this.calculatedAt = now;
    }

    /** Freezes the day as part of a monthly closing. */
    public void close(Instant now) {
        this.status = Status.CLOSED;
        this.calculatedAt = now;
    }

    public boolean isClosed() {
        return status == Status.CLOSED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getTargetMinutes() {
        return targetMinutes;
    }

    public int getWorkedMinutes() {
        return workedMinutes;
    }

    public int getBreakMinutes() {
        return breakMinutes;
    }

    public int getAbsenceMinutes() {
        return absenceMinutes;
    }

    public int getCreditedMinutes() {
        return creditedMinutes;
    }

    public int getBalanceMinutes() {
        return balanceMinutes;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }
}
