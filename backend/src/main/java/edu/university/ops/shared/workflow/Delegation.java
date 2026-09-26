package edu.university.ops.shared.workflow;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Time-bounded transfer of approval rights of one type (AGENT.md §18, ADR-011).
 * Resolved when tasks are queried: tasks stay assigned to the delegator.
 */
@Entity
@Table(name = "delegation")
public class Delegation {

    @Id
    private UUID id;

    private UUID delegatorId;
    private UUID delegateId;

    @Enumerated(EnumType.STRING)
    private ApprovalType approvalType;

    private LocalDate validFrom;
    private LocalDate validTo;
    private boolean active;
    private Instant createdAt;

    protected Delegation() {
    }

    Delegation(UUID delegatorId, UUID delegateId, ApprovalType approvalType, LocalDate validFrom, LocalDate validTo,
               Instant now) {
        this.id = UUID.randomUUID();
        this.delegatorId = delegatorId;
        this.delegateId = delegateId;
        this.approvalType = approvalType;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.active = true;
        this.createdAt = now;
    }

    public boolean isEffectiveOn(LocalDate date) {
        return active && !date.isBefore(validFrom) && !date.isAfter(validTo);
    }

    void revoke() {
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDelegatorId() {
        return delegatorId;
    }

    public UUID getDelegateId() {
        return delegateId;
    }

    public ApprovalType getApprovalType() {
        return approvalType;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
