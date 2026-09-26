package edu.university.ops.absence.persistence;

import edu.university.ops.absence.domain.AbsenceRepositories;
import edu.university.ops.absence.domain.AbsenceRequest;
import edu.university.ops.absence.domain.AbsenceStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface JpaAbsenceRequestRepository
        extends Repository<AbsenceRequest, UUID>, AbsenceRepositories.AbsenceRequestRepository {

    @Override
    @Query("""
            select r from AbsenceRequest r
            where r.employeeId = :employeeId and r.status in :statuses
              and r.startDate <= :to and r.endDate >= :from""")
    List<AbsenceRequest> findOverlapping(@Param("employeeId") UUID employeeId,
                                         @Param("statuses") Collection<AbsenceStatus> statuses,
                                         @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Override
    @Query("""
            select r from AbsenceRequest r
            where r.employeeId in :employeeIds and r.status in :statuses
              and r.startDate <= :to and r.endDate >= :from
            order by r.startDate""")
    List<AbsenceRequest> findOverlappingForEmployees(@Param("employeeIds") Collection<UUID> employeeIds,
                                                     @Param("statuses") Collection<AbsenceStatus> statuses,
                                                     @Param("from") LocalDate from, @Param("to") LocalDate to);
}
