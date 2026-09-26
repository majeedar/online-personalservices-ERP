package edu.university.ops.absence.persistence;

import edu.university.ops.absence.domain.AbsenceRepositories;
import edu.university.ops.absence.domain.LeaveEntitlement;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface JpaLeaveEntitlementRepository
        extends Repository<LeaveEntitlement, UUID>, AbsenceRepositories.LeaveEntitlementRepository {
}
