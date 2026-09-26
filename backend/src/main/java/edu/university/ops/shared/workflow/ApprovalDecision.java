package edu.university.ops.shared.workflow;

import edu.university.ops.shared.workflow.WorkflowEnums.Decision;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Who decided what, and when. {@code onBehalfOfId} is set when the approver acted
 * as a delegate (ADR-011).
 */
@Entity
@Table(name = "approval_decision")
public class ApprovalDecision {

    @Id
    private UUID id;

    private UUID approverId;
    private UUID onBehalfOfId;

    @Enumerated(EnumType.STRING)
    private Decision decision;

    private String comment;
    private Instant decidedAt;

    protected ApprovalDecision() {
    }

    ApprovalDecision(UUID approverId, UUID onBehalfOfId, Decision decision, String comment, Instant decidedAt) {
        this.id = UUID.randomUUID();
        this.approverId = approverId;
        this.onBehalfOfId = onBehalfOfId;
        this.decision = decision;
        this.comment = comment;
        this.decidedAt = decidedAt;
    }

    /** Retention: the free-text comment is personal data; the decision itself is kept. */
    void removeComment() {
        this.comment = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getApproverId() {
        return approverId;
    }

    public UUID getOnBehalfOfId() {
        return onBehalfOfId;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getComment() {
        return comment;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
