package edu.university.ops.time;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/** Public query API of the time module (dashboard, reports). */
public interface TimeAccounts {

    /** Sum of daily balances from {@code from} to {@code to} (inclusive), in minutes. */
    int balanceMinutes(UUID employeeId, LocalDate from, LocalDate to);

    /** Recalculates the stored account of the given days (batch, AGENT.md §27.6). */
    int recalculate(UUID employeeId, LocalDate from, LocalDate to);

    /** Totals of one month (days up to today). */
    MonthSummary monthSummary(UUID employeeId, YearMonth month);

    record MonthSummary(int targetMinutes, int workedMinutes, int absenceMinutes, int creditedMinutes,
                        int balanceMinutes, int incompleteDays) {
    }
}
