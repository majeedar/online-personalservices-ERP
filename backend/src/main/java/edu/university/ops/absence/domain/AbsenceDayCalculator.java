package edu.university.ops.absence.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Work-schedule-aware absence calculation (AGENT.md §13.6, Demo Scenario 2).
 * Pure and deterministic: the same inputs always give the same days.
 *
 * <p>A day counts only if the employee's schedule makes it a working day and it is
 * not a public holiday. Only counted days reduce the entitlement (one day each for
 * deducting leave types) and carry planned / credited minutes.
 */
public final class AbsenceDayCalculator {

    /** Longest request accepted, to bound the work per request. */
    public static final int MAX_DAYS = 366;

    private AbsenceDayCalculator() {
    }

    /** The schedule's plan for a date. */
    public record DayPlan(int targetMinutes, boolean workingDay) {
    }

    public record CalculatedDay(LocalDate date, int plannedMinutes, int creditedMinutes,
                                BigDecimal entitlementDeduction, AbsenceDay.Kind kind) {

        public boolean counts() {
            return kind == AbsenceDay.Kind.WORKING_DAY;
        }
    }

    /**
     * @param plan     the employee's schedule per date; empty if no schedule is valid on that date
     * @param holidays public holidays in the range
     */
    public static List<CalculatedDay> calculate(LocalDate start, LocalDate end, LeaveType leaveType,
                                                Function<LocalDate, Optional<DayPlan>> plan, Set<LocalDate> holidays) {
        List<CalculatedDay> days = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            Optional<DayPlan> dayPlan = plan.apply(date);
            AbsenceDay.Kind kind;
            if (dayPlan.isEmpty()) {
                kind = AbsenceDay.Kind.NO_SCHEDULE;
            } else if (holidays.contains(date)) {
                kind = AbsenceDay.Kind.HOLIDAY;
            } else if (!dayPlan.get().workingDay() || dayPlan.get().targetMinutes() == 0) {
                kind = AbsenceDay.Kind.NON_WORKING_DAY;
            } else {
                kind = AbsenceDay.Kind.WORKING_DAY;
            }
            if (kind == AbsenceDay.Kind.WORKING_DAY) {
                int planned = dayPlan.get().targetMinutes();
                days.add(new CalculatedDay(date, planned, leaveType.isCreditsWorkingTime() ? planned : 0,
                        leaveType.isDeductsEntitlement() ? BigDecimal.ONE : BigDecimal.ZERO, kind));
            } else {
                days.add(new CalculatedDay(date, 0, 0, BigDecimal.ZERO, kind));
            }
        }
        return days;
    }
}
