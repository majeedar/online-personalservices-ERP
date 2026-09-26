package edu.university.ops.absence.application;

import edu.university.ops.absence.domain.AbsenceRequest;
import edu.university.ops.employee.EmployeeDirectory;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.security.Role;
import edu.university.ops.shared.workflow.ApprovalType;
import edu.university.ops.shared.workflow.WorkflowService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Object-level read access to absence requests (AGENT.md §47):
 * the employee, HR admins, the employee's current absence approvers, and anyone
 * involved in the request's workflow (including an active delegate).
 */
@Component
class AbsenceAccessPolicy {

    private final EmployeeDirectory employees;
    private final WorkflowService workflow;
    private final Clock clock;

    AbsenceAccessPolicy(EmployeeDirectory employees, WorkflowService workflow, Clock clock) {
        this.employees = employees;
        this.workflow = workflow;
        this.clock = clock;
    }

    boolean canView(OpsPrincipal principal, AbsenceRequest request) {
        if (request.getEmployeeId().equals(principal.employeeId()) || principal.hasRole(Role.HR_ADMIN)) {
            return true;
        }
        if (employees.isApproverOf(principal.employeeId(), request.getEmployeeId(), ApprovalType.ABSENCE,
                LocalDate.now(clock))) {
            return true;
        }
        return request.getWorkflowInstanceId() != null
                && workflow.isInvolved(request.getWorkflowInstanceId(), principal);
    }
}
