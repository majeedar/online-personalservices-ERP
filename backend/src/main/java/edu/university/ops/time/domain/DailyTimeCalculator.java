package edu.university.ops.time.domain;

import edu.university.ops.time.domain.TimeEntry.Type;
import edu.university.ops.time.domain.TimeSequence.Event;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Daily working-time calculation (AGENT.md §15.3):
 *
 * <pre>
 * creditedMinutes = workedMinutes + eligibleAbsenceMinutes
 * balanceMinutes  = creditedMinutes - targetMinutes
 * </pre>
 *
 * <p>Breaks: recorded breaks are not working time. In addition the statutory
 * minimum break is enforced (German ArbZG §4 as a configurable default): more than
 * 6 hours of work require 30 minutes of break, more than 9 hours 45 minutes; any
 * shortfall is deducted from worked time.
 *
 * <p>An open interval (clocked in, not yet out) counts up to {@code openUntil}
 * when given (today), otherwise it is ignored and the day is marked incomplete.
 */
public final class DailyTimeCalculator {

    private DailyTimeCalculator() {
    }

    public record Result(int workedMinutes, int breakMinutes, int creditedMinutes, int balanceMinutes,
                         boolean incomplete, boolean statutoryBreakApplied) {
    }

    public static Result calculate(List<Event> orderedEvents, int targetMinutes, int creditedAbsenceMinutes,
                                   Instant openUntil, boolean statutoryBreaks) {
        long presence = 0;
        long breaks = 0;
        Instant workStart = null;
        Instant breakStart = null;
        for (Event e : orderedEvents) {
            switch (e.type()) {
                case CLOCK_IN -> workStart = e.timestamp();
                case CLOCK_OUT -> {
                    if (workStart != null) {
                        presence += minutes(workStart, e.timestamp());
                        workStart = null;
                    }
                }
                case BREAK_START -> breakStart = e.timestamp();
                case BREAK_END -> {
                    if (breakStart != null) {
                        breaks += minutes(breakStart, e.timestamp());
                        breakStart = null;
                    }
                }
            }
        }
        boolean incomplete = workStart != null;
        if (workStart != null && openUntil != null && openUntil.isAfter(workStart)) {
            presence += minutes(workStart, openUntil);
            if (breakStart != null) {
                breaks += minutes(breakStart, openUntil);
            }
        }
        long worked = Math.max(0, presence - breaks);

        boolean applied = false;
        if (statutoryBreaks) {
            long required = worked > 9 * 60 ? 45 : worked > 6 * 60 ? 30 : 0;
            if (breaks < required) {
                long shortfall = required - breaks;
                worked = Math.max(0, worked - shortfall);
                breaks = required;
                applied = true;
            }
        }
        int credited = (int) worked + creditedAbsenceMinutes;
        return new Result((int) worked, (int) breaks, credited, credited - targetMinutes, incomplete, applied);
    }

    private static long minutes(Instant from, Instant to) {
        return Math.max(0, Duration.between(from, to).toMinutes());
    }

    /** Convenience for tests. */
    public static Event event(Instant at, Type type) {
        return new Event(at, type);
    }
}
