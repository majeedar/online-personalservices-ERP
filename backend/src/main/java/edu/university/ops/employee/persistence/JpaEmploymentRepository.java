package edu.university.ops.employee.persistence;

import edu.university.ops.employee.domain.Employment;
import edu.university.ops.employee.domain.EmployeeRepositories;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data adapter for {@link EmployeeRepositories.EmploymentRepository}. */
public interface JpaEmploymentRepository extends Repository<Employment, UUID>, EmployeeRepositories.EmploymentRepository {
}
