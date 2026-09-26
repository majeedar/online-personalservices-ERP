package edu.university.ops.calendar;

import java.time.LocalDate;
import java.util.Map;

/** Public API of the calendar module. Uses the configured holiday region ({@code ops.holiday-region}). */
public interface HolidayCalendar {

    /** Public holidays in [from, to], mapped to their names. */
    Map<LocalDate, String> holidaysBetween(LocalDate from, LocalDate to);

    default boolean isHoliday(LocalDate date) {
        return holidaysBetween(date, date).containsKey(date);
    }
}
