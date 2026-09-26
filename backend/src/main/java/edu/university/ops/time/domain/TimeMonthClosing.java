package edu.university.ops.time.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * The closing of one month's time accounts for the whole university. While it is
 * CLOSED, the month's daily accounts are frozen.
 */
@Entity
@Table(name = "time_month_closing")
public class TimeMonthClosing {

    public enum Status { CLOSED, REOPENED }

    @Id
    private UUID id;

    private String yearMonth;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Instant closedAt;
    private String closedBy;
    private int employees;
    private int days;
    private Instant reopenedAt;
    private String reopenedBy;
    private String reopenReason;

    @Version
    private Long version;

    protected TimeMonthClosing() {
    }

    public TimeMonthClosing(YearMonth month, String closedBy, int employees, int days, Instant now) {
        this.id = UUID.randomUUID();
        this.yearMonth = month.toString();
        this.status = Status.CLOSED;
        this.closedAt = now;
        this.closedBy = closedBy;
        this.employees = employees;
        this.days = days;
    }

    public void reopen(String by, String reason, Instant now) {
        this.status = Status.REOPENED;
        this.reopenedBy = by;
        this.reopenReason = reason;
        this.reopenedAt = now;
    }

    public YearMonth month() {
        return YearMonth.parse(yearMonth);
    }

    public boolean covers(LocalDate date) {
        return status == Status.CLOSED && YearMonth.from(date).equals(month());
    }

    public UUID getId() {
        return id;
    }

    public String getYearMonth() {
        return yearMonth;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public int getEmployees() {
        return employees;
    }

    public int getDays() {
        return days;
    }

    public Instant getReopenedAt() {
        return reopenedAt;
    }

    public String getReopenedBy() {
        return reopenedBy;
    }

    public String getReopenReason() {
        return reopenReason;
    }
}
