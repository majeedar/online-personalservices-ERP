package edu.university.ops.time.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence ports of the time module (ADR-002). */
public final class TimeRepositories {

    private TimeRepositories() {
    }

    public interface TimeEntryRepository {
        TimeEntry save(TimeEntry entry);

        Optional<TimeEntry> findById(UUID id);

        List<TimeEntry> findByEmployeeIdAndBusinessDateAndVoidedAtIsNullOrderByTimestamp(UUID employeeId,
                                                                                       LocalDate date);

        List<TimeEntry> findByEmployeeIdAndBusinessDateOrderByTimestamp(UUID employeeId, LocalDate date);

        /** The employee's first booking; the time account starts on its date. */
        Optional<TimeEntry> findFirstByEmployeeIdAndVoidedAtIsNullOrderByBusinessDate(UUID employeeId);

        List<TimeEntry> findByEmployeeIdAndBusinessDateBetweenAndVoidedAtIsNullOrderByTimestamp(UUID employeeId,
                                                                                              LocalDate from,
                                                                                              LocalDate to);

        /** Pairs (employee, business date) that changed recently; for the nightly recalculation. */
        List<TimeEntry> findByCreatedAtAfter(java.time.Instant after);
    }

    public interface TimeAccountDayRepository {
        TimeAccountDay save(TimeAccountDay day);

        Optional<TimeAccountDay> findByEmployeeIdAndDate(UUID employeeId, LocalDate date);

        List<TimeAccountDay> findByEmployeeIdAndDateBetweenOrderByDate(UUID employeeId, LocalDate from, LocalDate to);

        List<TimeAccountDay> findByEmployeeIdInAndDateBetween(Collection<UUID> employeeIds, LocalDate from,
                                                              LocalDate to);
    }

    public interface TimeCorrectionRepository {
        TimeCorrectionRequest save(TimeCorrectionRequest request);

        Optional<TimeCorrectionRequest> findById(UUID id);

        List<TimeCorrectionRequest> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

        boolean existsByEmployeeIdAndDateAndStatus(UUID employeeId, LocalDate date,
                                                   TimeCorrectionRequest.Status status);

        List<TimeCorrectionRequest> findByEmployeeIdAndStatusAndDateBetween(UUID employeeId,
                                                                          TimeCorrectionRequest.Status status,
                                                                          LocalDate from, LocalDate to);

        long countByStatusAndDateBetween(TimeCorrectionRequest.Status status, LocalDate from, LocalDate to);

        long count();
    }

    public interface TimeMonthClosingRepository {
        TimeMonthClosing save(TimeMonthClosing closing);

        TimeMonthClosing saveAndFlush(TimeMonthClosing closing);

        Optional<TimeMonthClosing> findByYearMonthAndStatus(String yearMonth, TimeMonthClosing.Status status);

        List<TimeMonthClosing> findByStatus(TimeMonthClosing.Status status);

        List<TimeMonthClosing> findByYearMonthOrderByClosedAtDesc(String yearMonth);

        long count();
    }
}
