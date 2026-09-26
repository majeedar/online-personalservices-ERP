package edu.university.ops.shared.audit;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/** One immutable audit record (AGENT.md §20). The table is append-only (ADR-008). */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLogEntry implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID actorEmployeeId;
    private String actorUsername;
    private String action;
    private String entityType;
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode oldValueJson;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode newValueJson;

    @Column(name = "timestamp")
    private Instant timestamp;

    private String correlationId;

    @Transient
    private boolean isNew;

    protected AuditLogEntry() {
    }

    AuditLogEntry(UUID actorEmployeeId, String actorUsername, String action, String entityType, String entityId,
                  JsonNode oldValue, JsonNode newValue, Instant timestamp, String correlationId) {
        this.id = UUID.randomUUID();
        this.actorEmployeeId = actorEmployeeId;
        this.actorUsername = actorUsername;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.oldValueJson = oldValue;
        this.newValueJson = newValue;
        this.timestamp = timestamp;
        this.correlationId = correlationId;
        this.isNew = true;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID getActorEmployeeId() {
        return actorEmployeeId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public JsonNode getOldValueJson() {
        return oldValueJson;
    }

    public JsonNode getNewValueJson() {
        return newValueJson;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
