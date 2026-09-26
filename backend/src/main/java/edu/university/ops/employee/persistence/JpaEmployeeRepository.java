package edu.university.ops.employee.persistence;

import edu.university.ops.employee.domain.Employee;
import edu.university.ops.employee.domain.EmployeeRepositories;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Spring Data adapter for {@link EmployeeRepositories.EmployeeRepository}. */
public interface JpaEmployeeRepository extends Repository<Employee, UUID>, EmployeeRepositories.EmployeeRepository {

    @Override
    @Query(value = """
            select e.* from employee e
            where e.active
              and (lower(e.first_name) like lower(concat('%', :text, '%'))
                   or lower(e.last_name) like lower(concat('%', :text, '%'))
                   or lower(e.first_name || ' ' || e.last_name) like lower(concat('%', :text, '%')))
            order by e.last_name, e.first_name
            limit 20""", nativeQuery = true)
    List<Employee> searchActiveByName(String text);
}
