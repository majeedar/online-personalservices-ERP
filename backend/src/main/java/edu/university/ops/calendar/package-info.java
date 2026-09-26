/**
 * Public-holiday calendar (AGENT.md §13.3), used by absence and time calculations.
 *
 * <p>Public API: {@link edu.university.ops.calendar.HolidayCalendar}.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Calendar",
        allowedDependencies = {"shared"})
package edu.university.ops.calendar;
