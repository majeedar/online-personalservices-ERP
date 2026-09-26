package edu.university.ops.employee.persistence;

import edu.university.ops.employee.domain.WorkSchedule;
import edu.university.ops.employee.domain.EmployeeRepositories;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data adapter for {@link EmployeeRepositories.WorkScheduleRepository}. */
public interface JpaWorkScheduleRepository extends Repository<WorkSchedule, UUID>, EmployeeRepositories.WorkScheduleRepository {
}
