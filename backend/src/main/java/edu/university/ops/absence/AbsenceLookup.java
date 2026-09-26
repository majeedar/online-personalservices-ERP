package edu.university.ops.absence;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Public query API of the absence module for the time module (AGENT.md §15.4). */
public interface AbsenceLookup {

    /**
     * Effective (approved, incl. pending cancellation) absence per date.
     *
     * @return date -> minutes; planned = the scheduled target covered by the absence,
     *         credited = the part credited as working time (0 e.g. for a flex day)
     */
    Map<LocalDate, AbsenceMinutes> approvedAbsences(UUID employeeId, LocalDate from, LocalDate to);

    record AbsenceMinutes(int plannedMinutes, int creditedMinutes, String leaveTypeCode) {
    }
}
