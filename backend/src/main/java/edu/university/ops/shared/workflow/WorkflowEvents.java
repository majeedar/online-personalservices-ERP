package edu.university.ops.shared.workflow;

import edu.university.ops.shared.workflow.WorkflowEnums.InstanceStatus;
import java.util.UUID;

/**
 * Events published by the workflow engine. They are delivered synchronously,
 * inside the deciding transaction, so the owning module's status change commits
 * together with the decision (ADR-004).
 */
public final class WorkflowEvents {

    private WorkflowEvents() {
    }

    /** A new task became open, e.g. to notify the assignee and active delegates. */
    public record TaskCreated(UUID taskId, UUID instanceId, String definitionCode, String businessObjectType,
                              UUID businessObjectId, UUID assignedEmployeeId, String assignedRole,
                              ApprovalType approvalType, String title) {
    }

    /** The workflow reached a final outcome: APPROVED, REJECTED or RETURNED. */
    public record Completed(UUID instanceId, String definitionCode, String businessObjectType, UUID businessObjectId,
                            InstanceStatus outcome, UUID decidedBy, String comment) {

        public boolean concerns(String type) {
            return businessObjectType.equals(type);
        }
    }
}
