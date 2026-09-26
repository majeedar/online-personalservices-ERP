package edu.university.ops.employee.persistence;

import edu.university.ops.employee.domain.UserRole;
import edu.university.ops.employee.domain.EmployeeRepositories;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data adapter for {@link EmployeeRepositories.UserRoleRepository}. */
public interface JpaUserRoleRepository extends Repository<UserRole, UUID>, EmployeeRepositories.UserRoleRepository {
}
