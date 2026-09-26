package edu.university.ops.shared.integration;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An external export waiting for (or finished with) delivery (AGENT.md §50). */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    public enum Status { PENDING, PROCESSED, FAILED }

    @Id
    private UUID id;

    private String eventType;
    private String aggregateType;
    private UUID aggregateId;
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode payload;

    @Enumerated(EnumType.STRING)
    private Status status;

    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;
    private Instant createdAt;
    private Instant processedAt;

    protected OutboxEvent() {
    }

    OutboxEvent(String eventType, String aggregateType, UUID aggregateId, String idempotencyKey, JsonNode payload,
                Instant now) {
        this.id = UUID.randomUUID();
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.idempotencyKey = idempotencyKey;
        this.payload = payload;
        this.status = Status.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    void processed(Instant now) {
        this.status = Status.PROCESSED;
        this.processedAt = now;
        this.attempts++;
        this.lastError = null;
    }

    /** @return true if the event gave up (no further automatic attempts) */
    boolean failed(String error, int maxAttempts, Instant now) {
        this.attempts++;
        this.lastError = error.length() > 1000 ? error.substring(0, 1000) : error;
        if (attempts >= maxAttempts) {
            this.status = Status.FAILED;
            return true;
        }
        // Exponential backoff: 10s, 20s, 40s, ...
        this.nextAttemptAt = now.plus(Duration.ofSeconds(10L << Math.min(attempts - 1, 10)));
        return false;
    }

    void retryNow(Instant now) {
        this.status = Status.PENDING;
        this.nextAttemptAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
