package edu.university.ops.time.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single clock event (AGENT.md §15.1). Never deleted: corrections void the
 * original entry and add a new one, so the full history stays auditable.
 */
@Entity
@Table(name = "time_entry")
public class TimeEntry {

    public enum Type { CLOCK_IN, CLOCK_OUT, BREAK_START, BREAK_END }

    public enum Source { WEB, ADMIN, IMPORT, MOCK_TERMINAL }

    @Id
    private UUID id;

    private UUID employeeId;

    @Column(name = "timestamp")
    private Instant timestamp;

    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    private Type type;

    @Enumerated(EnumType.STRING)
    private Source source;

    private Instant createdAt;
    private Instant voidedAt;
    private UUID correctionRequestId;

    protected TimeEntry() {
    }

    public TimeEntry(UUID employeeId, Instant timestamp, LocalDate businessDate, Type type, Source source,
                     Instant createdAt, UUID correctionRequestId) {
        this.id = UUID.randomUUID();
        this.employeeId = employeeId;
        this.timestamp = timestamp;
        this.businessDate = businessDate;
        this.type = type;
        this.source = source;
        this.createdAt = createdAt;
        this.correctionRequestId = correctionRequestId;
    }

    public void voidBy(UUID correctionRequestId, Instant now) {
        this.voidedAt = now;
        this.correctionRequestId = correctionRequestId;
    }

    public boolean isVoided() {
        return voidedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public Type getType() {
        return type;
    }

    public Source getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public UUID getCorrectionRequestId() {
        return correctionRequestId;
    }
}
