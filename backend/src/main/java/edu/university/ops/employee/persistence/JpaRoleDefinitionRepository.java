package edu.university.ops.employee.persistence;

import edu.university.ops.employee.domain.EmployeeRepositories;
import edu.university.ops.employee.domain.RoleDefinition;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface JpaRoleDefinitionRepository
        extends Repository<RoleDefinition, UUID>, EmployeeRepositories.RoleDefinitionRepository {
}
