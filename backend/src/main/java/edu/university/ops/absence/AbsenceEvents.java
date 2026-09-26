package edu.university.ops.absence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Domain events of the absence module (AGENT.md §49). Consumers (e.g. the time
 * module) receive them through the event publication registry (ADR-006) and
 * re-read current data via {@link AbsenceLookup}, so events carry only references.
 */
public final class AbsenceEvents {

    private AbsenceEvents() {
    }

    public record AbsenceSubmitted(UUID requestId, UUID employeeId, LocalDate startDate, LocalDate endDate) {
    }

    public record AbsenceApproved(UUID requestId, UUID employeeId, List<LocalDate> dates) {
    }

    public record AbsenceRejected(UUID requestId, UUID employeeId) {
    }

    public record AbsenceCancelled(UUID requestId, UUID employeeId, List<LocalDate> dates) {
    }
}
