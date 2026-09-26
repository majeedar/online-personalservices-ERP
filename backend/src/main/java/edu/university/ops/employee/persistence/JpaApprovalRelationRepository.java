package edu.university.ops.employee.persistence;

import edu.university.ops.employee.domain.ApprovalRelation;
import edu.university.ops.employee.domain.EmployeeRepositories;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data adapter for {@link EmployeeRepositories.ApprovalRelationRepository}. */
public interface JpaApprovalRelationRepository extends Repository<ApprovalRelation, UUID>, EmployeeRepositories.ApprovalRelationRepository {
}
