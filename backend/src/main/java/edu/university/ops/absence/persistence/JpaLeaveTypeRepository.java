package edu.university.ops.absence.persistence;

import edu.university.ops.absence.domain.AbsenceRepositories;
import edu.university.ops.absence.domain.LeaveType;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface JpaLeaveTypeRepository extends Repository<LeaveType, UUID>, AbsenceRepositories.LeaveTypeRepository {
}
