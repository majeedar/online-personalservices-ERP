package edu.university.ops.shared.integration;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A failed record or call; stays visible to admins until resolved (AGENT.md §26). */
@Entity
@Table(name = "integration_error")
public class IntegrationError {

    @Id
    private UUID id;

    private UUID integrationRunId;
    private String externalReference;
    private String errorCode;
    private String errorMessage;
    private int retryCount;
    private UUID outboxEventId;
    private Instant createdAt;
    private boolean resolved;
    private Instant resolvedAt;

    protected IntegrationError() {
    }

    IntegrationError(UUID runId, String externalReference, String errorCode, String errorMessage, int retryCount,
                     UUID outboxEventId, Instant now) {
        this.id = UUID.randomUUID();
        this.integrationRunId = runId;
        this.externalReference = externalReference;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage;
        this.retryCount = retryCount;
        this.outboxEventId = outboxEventId;
        this.createdAt = now;
    }

    void resolve(Instant now) {
        this.resolved = true;
        this.resolvedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIntegrationRunId() {
        return integrationRunId;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public UUID getOutboxEventId() {
        return outboxEventId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isResolved() {
        return resolved;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
