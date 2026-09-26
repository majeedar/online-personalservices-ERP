package edu.university.ops.time.domain;

import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request to add, change or remove a clock event (AGENT.md §15.5). ADD covers a
 * missing entry such as a forgotten clock-out (Demo Scenario 6).
 */
@Entity
@Table(name = "time_correction_request")
public class TimeCorrectionRequest {

    public enum Operation { ADD, MODIFY, DELETE }

    public enum Status { IN_APPROVAL, APPROVED, REJECTED, CANCELLED }

    @Id
    private UUID id;

    private UUID employeeId;
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    private Operation operation;

    private UUID originalTimeEntryId;
    private Instant requestedTimestamp;

    @Enumerated(EnumType.STRING)
    private TimeEntry.Type requestedType;

    private String reason;

    @Enumerated(EnumType.STRING)
    private Status status;

    private UUID workflowInstanceId;
    private Instant createdAt;
    private Instant decidedAt;

    @Version
    private Long version;

    protected TimeCorrectionRequest() {
    }

    public TimeCorrectionRequest(UUID employeeId, LocalDate date, Operation operation, UUID originalTimeEntryId,
                                 Instant requestedTimestamp, TimeEntry.Type requestedType, String reason,
                                 Instant now) {
        this.id = UUID.randomUUID();
        this.employeeId = employeeId;
        this.date = date;
        this.operation = operation;
        this.originalTimeEntryId = originalTimeEntryId;
        this.requestedTimestamp = requestedTimestamp;
        this.requestedType = requestedType;
        this.reason = reason;
        this.status = Status.IN_APPROVAL;
        this.createdAt = now;
    }

    public void attachWorkflow(UUID workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public void decide(boolean approved, Instant now) {
        if (status != Status.IN_APPROVAL) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE, "This correction was already decided.");
        }
        this.status = approved ? Status.APPROVED : Status.REJECTED;
        this.decidedAt = now;
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

    public Operation getOperation() {
        return operation;
    }

    public UUID getOriginalTimeEntryId() {
        return originalTimeEntryId;
    }

    public Instant getRequestedTimestamp() {
        return requestedTimestamp;
    }

    public TimeEntry.Type getRequestedType() {
        return requestedType;
    }

    public String getReason() {
        return reason;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
