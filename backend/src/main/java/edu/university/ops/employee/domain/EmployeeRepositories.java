package edu.university.ops.employee.domain;

import edu.university.ops.shared.workflow.ApprovalType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence ports of the employee module (ADR-002). Implemented by Spring Data
 * adapters in {@code employee.persistence}; the domain knows only these interfaces.
 */
public final class EmployeeRepositories {

    private EmployeeRepositories() {
    }

    public interface EmployeeRepository {
        Optional<Employee> findById(UUID id);

        Optional<Employee> findByUsername(String username);

        Optional<Employee> findByPersonnelNumber(String personnelNumber);

        List<Employee> findByIdIn(Collection<UUID> ids);

        List<Employee> findByActiveTrueAndOrganisationUnitIdIn(Collection<UUID> unitIds);

        /** Active employees whose first or last name contains {@code text} (case-insensitive), max 20. */
        List<Employee> searchActiveByName(String text);

        List<Employee> findAll();

        Employee save(Employee employee);
    }

    public interface EmploymentRepository {
        List<Employment> findByEmployeeIdOrderByStartDateDesc(UUID employeeId);

        Optional<Employment> findByExternalEmploymentId(String externalId);

        Employment save(Employment employment);
    }

    public interface WorkScheduleRepository {
        List<WorkSchedule> findByEmployeeIdOrderByValidFromDesc(UUID employeeId);

        WorkSchedule save(WorkSchedule schedule);
    }

    public interface UserRoleRepository {
        List<UserRole> findByEmployeeId(UUID employeeId);

        List<UserRole> findByRoleName(edu.university.ops.shared.security.Role role);

        UserRole save(UserRole userRole);
    }

    public interface RoleDefinitionRepository {
        Optional<RoleDefinition> findByName(edu.university.ops.shared.security.Role name);
    }

    public interface ApprovalRelationRepository {
        List<ApprovalRelation> findByEmployeeIdAndApprovalTypeOrderByPriority(UUID employeeId, ApprovalType type);

        List<ApprovalRelation> findByApproverIdAndEmployeeId(UUID approverId, UUID employeeId);

        List<ApprovalRelation> findByApproverIdAndApprovalType(UUID approverId, ApprovalType type);

        List<ApprovalRelation> findByEmployeeId(UUID employeeId);

        ApprovalRelation save(ApprovalRelation relation);
    }
}
