package edu.university.ops.shared.workflow;

import edu.university.ops.shared.security.Role;
import java.util.UUID;

/**
 * Definition of one approval step when a business module starts a workflow.
 * The module resolves approvers (it knows approval relations); the engine only
 * executes. At least one of {@code assignedEmployeeId} / {@code assignedRole} is set.
 *
 * @param stepType    e.g. SUPERVISOR_APPROVAL, FINANCIAL_APPROVAL
 * @param taskTitle   inbox title, e.g. "Approve annual leave – Erika Mustermann"
 */
public record StepSpec(String stepType, ApprovalType approvalType, UUID assignedEmployeeId, Role assignedRole,
                       String taskTitle, String taskDescription) {

    public StepSpec {
        if (assignedEmployeeId == null && assignedRole == null) {
            throw new IllegalArgumentException("A step needs an assignee or a role");
        }
    }

    public static StepSpec toPerson(String stepType, ApprovalType type, UUID employeeId, String title,
                                    String description) {
        return new StepSpec(stepType, type, employeeId, null, title, description);
    }

    public static StepSpec toRole(String stepType, ApprovalType type, Role role, String title, String description) {
        return new StepSpec(stepType, type, null, role, title, description);
    }
}
