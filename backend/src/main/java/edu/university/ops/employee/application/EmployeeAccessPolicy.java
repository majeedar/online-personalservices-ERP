package edu.university.ops.employee.application;

import edu.university.ops.shared.workflow.ApprovalType;
import edu.university.ops.employee.domain.EmployeeRepositories.ApprovalRelationRepository;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.security.Role;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Object-level authorization for employee master data (AGENT.md §47).
 *
 * <ul>
 *   <li>everyone may see their own record;</li>
 *   <li>HR_ADMIN may see all employees;</li>
 *   <li>an approver may see employees they currently hold any approval relation for.</li>
 * </ul>
 * ERP_ADMIN and AUDITOR deliberately get no personnel-data access by default.
 * Delegation is added with the workflow module (Phase 3).
 */
@Component
public class EmployeeAccessPolicy {

    private final ApprovalRelationRepository approvalRelations;

    EmployeeAccessPolicy(ApprovalRelationRepository approvalRelations) {
        this.approvalRelations = approvalRelations;
    }

    public boolean canViewEmployee(OpsPrincipal principal, UUID employeeId, LocalDate today) {
        if (principal.employeeId().equals(employeeId)) {
            return true;
        }
        if (principal.hasRole(Role.HR_ADMIN)) {
            return true;
        }
        return approvalRelations.findByApproverIdAndEmployeeId(principal.employeeId(), employeeId).stream()
                .anyMatch(r -> r.isValidOn(today));
    }

    public boolean isApproverOf(UUID approverId, UUID employeeId, ApprovalType type, LocalDate date) {
        return approvalRelations.findByApproverIdAndEmployeeId(approverId, employeeId).stream()
                .anyMatch(r -> r.getApprovalType() == type && r.isValidOn(date));
    }
}
