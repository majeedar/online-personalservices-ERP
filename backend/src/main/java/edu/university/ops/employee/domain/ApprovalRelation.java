package edu.university.ops.employee.domain;

import edu.university.ops.shared.workflow.ApprovalType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * "approverId approves employeeId's requests of approvalType" for a period
 * (AGENT.md §9). Replaces a single supervisor_id column.
 */
@Entity
@Table(name = "approval_relation")
public class ApprovalRelation {

    @Id
    private UUID id;

    private UUID employeeId;
    private UUID approverId;

    @Enumerated(EnumType.STRING)
    private ApprovalType approvalType;

    private int priority;
    private LocalDate validFrom;
    private LocalDate validTo;

    protected ApprovalRelation() {
    }

    public ApprovalRelation(UUID employeeId, UUID approverId, ApprovalType type, LocalDate validFrom) {
        this.id = UUID.randomUUID();
        this.employeeId = employeeId;
        this.approverId = approverId;
        this.approvalType = type;
        this.priority = 1;
        this.validFrom = validFrom;
    }

    /** Ends the relation (history is kept; relations are never deleted). */
    public void endOn(LocalDate lastDay) {
        this.validTo = lastDay.isBefore(validFrom) ? validFrom : lastDay;
    }

    public boolean isValidOn(LocalDate date) {
        return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getApproverId() {
        return approverId;
    }

    public ApprovalType getApprovalType() {
        return approvalType;
    }

    public int getPriority() {
        return priority;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }
}
