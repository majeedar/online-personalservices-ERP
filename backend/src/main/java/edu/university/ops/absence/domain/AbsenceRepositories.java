package edu.university.ops.absence.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence ports of the absence module (ADR-002). */
public final class AbsenceRepositories {

    private AbsenceRepositories() {
    }

    public interface LeaveTypeRepository {
        Optional<LeaveType> findById(UUID id);

        Optional<LeaveType> findByCode(String code);

        List<LeaveType> findByActiveTrueOrderByName();

        List<LeaveType> findAll();
    }

    public interface LeaveEntitlementRepository {
        Optional<LeaveEntitlement> findByEmployeeIdAndYearAndLeaveTypeId(UUID employeeId, int year, UUID leaveTypeId);

        List<LeaveEntitlement> findByEmployeeIdAndYear(UUID employeeId, int year);

        List<LeaveEntitlement> findByYear(int year);

        LeaveEntitlement save(LeaveEntitlement entitlement);
    }

    public interface AbsenceRequestRepository {
        AbsenceRequest save(AbsenceRequest request);

        Optional<AbsenceRequest> findById(UUID id);

        List<AbsenceRequest> findByEmployeeIdOrderByStartDateDesc(UUID employeeId);

        /** Requests of the employee in the given statuses that overlap [from, to]. */
        List<AbsenceRequest> findOverlapping(UUID employeeId, Collection<AbsenceStatus> statuses, LocalDate from,
                                             LocalDate to);

        /** Requests of several employees in the given statuses that overlap [from, to]. */
        List<AbsenceRequest> findOverlappingForEmployees(Collection<UUID> employeeIds,
                                                         Collection<AbsenceStatus> statuses, LocalDate from,
                                                         LocalDate to);

        List<AbsenceRequest> findByEmployeeIdAndStatusIn(UUID employeeId, Collection<AbsenceStatus> statuses);

        long count();
    }
}
