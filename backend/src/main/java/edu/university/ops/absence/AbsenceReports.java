package edu.university.ops.absence;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Read API for reporting (AGENT.md §81): leave usage per employee and year. */
public interface AbsenceReports {

    List<LeaveUsage> leaveUsage(int year);

    record LeaveUsage(UUID employeeId, BigDecimal entitled, BigDecimal used, BigDecimal reserved,
                      BigDecimal remaining) {
    }
}
