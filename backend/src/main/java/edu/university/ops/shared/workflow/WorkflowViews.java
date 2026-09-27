package edu.university.ops.shared.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import edu.university.ops.shared.i18n.Text;
import edu.university.ops.shared.workflow.WorkflowEnums.Decision;
import edu.university.ops.shared.workflow.WorkflowEnums.InstanceStatus;
import edu.university.ops.shared.workflow.WorkflowEnums.StepStatus;
import edu.university.ops.shared.workflow.WorkflowEnums.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read models returned by the workflow engine to business modules and the API. */
public final class WorkflowViews {

    private WorkflowViews() {
    }

    /**
     * @param viaDelegationFrom set when the viewer sees this task only because of a delegation
     * @param titleText         the title as a text, for messages in another reader's language
     */
    public record TaskView(UUID id, String title, String description, LocalDate dueDate, TaskStatus status,
                           Instant createdAt, String definitionCode, String businessObjectType, UUID businessObjectId,
                           String stepType, ApprovalType approvalType, UUID assignedEmployeeId,
                           String assignedEmployeeName, String assignedRole, UUID requesterId, String requesterName,
                           UUID viaDelegationFrom, @JsonIgnore Text titleText) {
    }

    /** Timeline of one workflow run (AGENT.md §88 "workflow timeline"). */
    public record InstanceHistory(UUID instanceId, String definitionCode, InstanceStatus status, Instant createdAt,
                                  Instant completedAt, List<StepHistory> steps) {
    }

    public record StepHistory(int stepNumber, String stepType, UUID assignedEmployeeId, String assignedEmployeeName,
                              String assignedRole, StepStatus status, Instant startedAt, Instant completedAt,
                              List<DecisionView> decisions) {
    }

    public record DecisionView(UUID approverId, String approverName, UUID onBehalfOfId, String onBehalfOfName,
                               Decision decision, String comment, Instant decidedAt) {
    }

    public record DecisionResult(UUID instanceId, InstanceStatus instanceStatus, String businessObjectType,
                                 UUID businessObjectId) {
    }
}
