package edu.university.ops.time.application;

import edu.university.ops.absence.AbsenceEvents;
import edu.university.ops.absence.AbsenceLookup;
import edu.university.ops.absence.AbsenceLookup.AbsenceMinutes;
import edu.university.ops.calendar.HolidayCalendar;
import edu.university.ops.employee.EmployeeDirectory;
import edu.university.ops.employee.EmployeeDirectory.WorkScheduleView;
import edu.university.ops.shared.configuration.OpsProperties;
import edu.university.ops.shared.directory.PersonDirectory;
import edu.university.ops.shared.notification.NotificationService;
import edu.university.ops.shared.notification.NotificationType;
import edu.university.ops.shared.security.Role;
import edu.university.ops.time.TimeAccounts;
import edu.university.ops.time.domain.DailyTimeCalculator;
import edu.university.ops.time.domain.TimeAccountDay;
import edu.university.ops.time.domain.TimeCorrectionRequest;
import edu.university.ops.time.domain.TimeEntry;
import edu.university.ops.time.domain.TimeMonthClosing;
import edu.university.ops.time.domain.TimeRepositories.TimeAccountDayRepository;
import edu.university.ops.time.domain.TimeRepositories.TimeCorrectionRepository;
import edu.university.ops.time.domain.TimeRepositories.TimeEntryRepository;
import edu.university.ops.time.domain.TimeRepositories.TimeMonthClosingRepository;
import edu.university.ops.time.domain.TimeSequence.Event;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily time accounts (AGENT.md §15.2–15.4). An open day is always derivable from
 * its inputs (entries, schedule, holidays, approved absences); the stored
 * {@link TimeAccountDay} rows are kept current for reporting and batch jobs.
 * Days of a closed month are frozen: their stored values are authoritative and
 * recalculation leaves them untouched.
 */
@Service
@Transactional
public class TimeAccountService implements TimeAccounts {

    /** Upper bound for on-the-fly range calculations. */
    static final int MAX_RANGE_DAYS = 400;

    private final TimeEntryRepository entries;
    private final TimeAccountDayRepository accountDays;
    private final TimeCorrectionRepository corrections;
    private final TimeMonthClosingRepository closings;
    private final EmployeeDirectory employees;
    private final HolidayCalendar holidays;
    private final AbsenceLookup absences;
    private final NotificationService notifications;
    private final PersonDirectory persons;
    private final OpsProperties properties;
    private final Clock clock;

    TimeAccountService(TimeEntryRepository entries, TimeAccountDayRepository accountDays,
                       TimeCorrectionRepository corrections, TimeMonthClosingRepository closings,
                       EmployeeDirectory employees, HolidayCalendar holidays, AbsenceLookup absences,
                       NotificationService notifications, PersonDirectory persons, OpsProperties properties,
                       Clock clock) {
        this.entries = entries;
        this.accountDays = accountDays;
        this.corrections = corrections;
        this.closings = closings;
        this.employees = employees;
        this.holidays = holidays;
        this.absences = absences;
        this.notifications = notifications;
        this.persons = persons;
        this.properties = properties;
        this.clock = clock;
    }

    public record DayView(LocalDate date, int targetMinutes, int workedMinutes, int breakMinutes, int absenceMinutes,
                          int creditedMinutes, int balanceMinutes, String absenceType, String holidayName,
                          boolean incomplete, boolean statutoryBreakApplied, TimeAccountDay.Status status,
                          boolean future, boolean accounted) {
    }

    /** Calculates every day in [from, to], loading each input once for the whole range. */
    @Transactional(readOnly = true)
    public List<DayView> computeRange(UUID employeeId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new IllegalArgumentException("Invalid range");
        }
        List<WorkScheduleView> schedules = employees.workSchedules(employeeId);
        Map<LocalDate, String> holidayNames = holidays.holidaysBetween(from, to);
        Map<LocalDate, AbsenceMinutes> absenceDays = absences.approvedAbsences(employeeId, from, to);
        Map<LocalDate, List<TimeEntry>> entriesByDate = entries
                .findByEmployeeIdAndBusinessDateBetweenAndVoidedAtIsNullOrderByTimestamp(employeeId, from, to).stream()
                .collect(Collectors.groupingBy(TimeEntry::getBusinessDate));
        Set<LocalDate> pendingCorrections = corrections
                .findByEmployeeIdAndStatusAndDateBetween(employeeId, TimeCorrectionRequest.Status.IN_APPROVAL, from, to)
                .stream().map(TimeCorrectionRequest::getDate).collect(Collectors.toSet());
        Set<YearMonth> closedMonths = closedMonths();
        Map<LocalDate, TimeAccountDay> frozen = closedMonths.isEmpty() ? Map.of()
                : accountDays.findByEmployeeIdAndDateBetweenOrderByDate(employeeId, from, to).stream()
                .filter(TimeAccountDay::isClosed)
                .collect(Collectors.toMap(TimeAccountDay::getDate, Function.identity()));

        // The time account starts with the employee's first booking (opening balance 0); earlier days
        // show their target but do not count, so introducing time recording does not create a deficit.
        LocalDate accountingStart = entries.findFirstByEmployeeIdAndVoidedAtIsNullOrderByBusinessDate(employeeId)
                .map(TimeEntry::getBusinessDate).orElse(null);

        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        List<DayView> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            LocalDate d = date;
            String holiday = holidayNames.get(d);
            AbsenceMinutes absence = absenceDays.get(d);
            boolean future = d.isAfter(today);
            boolean accounted = !future && accountingStart != null && !d.isBefore(accountingStart);
            TimeAccountDay closed = frozen.get(d);
            if (closed != null) {
                result.add(new DayView(d, closed.getTargetMinutes(), closed.getWorkedMinutes(),
                        closed.getBreakMinutes(), closed.getAbsenceMinutes(), closed.getCreditedMinutes(),
                        closed.getBalanceMinutes(), absence == null ? null : absence.leaveTypeCode(), holiday, false,
                        false, TimeAccountDay.Status.CLOSED, false, accounted));
                continue;
            }
            int target = holiday != null ? 0 : schedules.stream().filter(s -> s.isValidOn(d)).findFirst()
                    .map(s -> s.targetMinutesOn(d.getDayOfWeek())).orElse(0);
            List<Event> events = entriesByDate.getOrDefault(d, List.of()).stream()
                    .map(e -> new Event(e.getTimestamp(), e.getType())).toList();
            var calc = DailyTimeCalculator.calculate(events, target, absence == null ? 0 : absence.creditedMinutes(),
                    d.equals(today) ? now : null, properties.time().statutoryBreaks());
            TimeAccountDay.Status status = pendingCorrections.contains(d) ? TimeAccountDay.Status.CORRECTION_PENDING
                    : (!d.isBefore(today) || calc.incomplete()) ? TimeAccountDay.Status.OPEN
                    : TimeAccountDay.Status.CALCULATED;
            // Future days carry their target but do not affect the balance yet.
            result.add(new DayView(d, target, calc.workedMinutes(), calc.breakMinutes(),
                    absence == null ? 0 : absence.plannedMinutes(), calc.creditedMinutes(),
                    accounted ? calc.balanceMinutes() : 0, absence == null ? null : absence.leaveTypeCode(), holiday,
                    calc.incomplete(), calc.statutoryBreakApplied(), status, future, accounted));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public DayView compute(UUID employeeId, LocalDate date) {
        return computeRange(employeeId, date, date).getFirst();
    }

    /**
     * Recalculates and stores past and current days of the range, except days of
     * closed months; returns the number stored.
     */
    @Override
    public int recalculate(UUID employeeId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = to.isAfter(today) ? today : to;
        if (end.isBefore(from)) {
            return 0;
        }
        Set<YearMonth> closedMonths = closedMonths();
        Instant now = Instant.now(clock);
        int stored = 0;
        for (DayView v : computeRange(employeeId, from, end)) {
            if (closedMonths.contains(YearMonth.from(v.date()))) {
                continue;
            }
            store(employeeId, v, now);
            stored++;
        }
        return stored;
    }

    public void recalculateDay(UUID employeeId, LocalDate date) {
        recalculate(employeeId, date, date);
    }

    /** Final calculation of a month for one employee, then frozen; returns the number of days closed. */
    int closeMonth(UUID employeeId, YearMonth month) {
        Instant now = Instant.now(clock);
        int closed = 0;
        for (DayView v : computeRange(employeeId, month.atDay(1), month.atEndOfMonth())) {
            TimeAccountDay day = store(employeeId, v, now);
            day.close(now);
            closed++;
        }
        return closed;
    }

    /** Unfreezes a reopened month (its closing record must already be REOPENED). */
    int reopenMonth(UUID employeeId, YearMonth month) {
        Instant now = Instant.now(clock);
        int reopened = 0;
        for (TimeAccountDay day : accountDays.findByEmployeeIdAndDateBetweenOrderByDate(employeeId, month.atDay(1),
                month.atEndOfMonth())) {
            if (day.isClosed()) {
                day.update(day.getTargetMinutes(), day.getWorkedMinutes(), day.getBreakMinutes(),
                        day.getAbsenceMinutes(), day.getCreditedMinutes(), day.getBalanceMinutes(),
                        TimeAccountDay.Status.CALCULATED, now);
                reopened++;
            }
        }
        recalculate(employeeId, month.atDay(1), month.atEndOfMonth());
        return reopened;
    }

    @Transactional(readOnly = true)
    public boolean isClosed(LocalDate date) {
        return closings.findByYearMonthAndStatus(YearMonth.from(date).toString(), TimeMonthClosing.Status.CLOSED)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public Set<YearMonth> closedMonths() {
        return closings.findByStatus(TimeMonthClosing.Status.CLOSED).stream().map(TimeMonthClosing::month)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    @Transactional(readOnly = true)
    public int balanceMinutes(UUID employeeId, LocalDate from, LocalDate to) {
        return computeRange(employeeId, from, to).stream().mapToInt(DayView::balanceMinutes).sum();
    }

    @Override
    @Transactional(readOnly = true)
    public MonthSummary monthSummary(UUID employeeId, YearMonth month) {
        List<DayView> days = computeRange(employeeId, month.atDay(1), month.atEndOfMonth()).stream()
                .filter(d -> !d.future()).toList();
        return new MonthSummary(days.stream().mapToInt(DayView::targetMinutes).sum(),
                days.stream().mapToInt(DayView::workedMinutes).sum(),
                days.stream().mapToInt(DayView::absenceMinutes).sum(),
                days.stream().mapToInt(DayView::creditedMinutes).sum(),
                days.stream().mapToInt(DayView::balanceMinutes).sum(),
                (int) days.stream().filter(DayView::incomplete).count());
    }

    private TimeAccountDay store(UUID employeeId, DayView v, Instant now) {
        TimeAccountDay day = accountDays.findByEmployeeIdAndDate(employeeId, v.date())
                .orElseGet(() -> new TimeAccountDay(employeeId, v.date()));
        day.update(v.targetMinutes(), v.workedMinutes(), v.breakMinutes(), v.absenceMinutes(), v.creditedMinutes(),
                v.balanceMinutes(), v.status(), now);
        return accountDays.save(day);
    }

    // ------------------------------------------- absence integration (§15.4)

    @ApplicationModuleListener
    void on(AbsenceEvents.AbsenceApproved event) {
        recalculateDates(event.employeeId(), event.dates());
    }

    @ApplicationModuleListener
    void on(AbsenceEvents.AbsenceCancelled event) {
        recalculateDates(event.employeeId(), event.dates());
    }

    private void recalculateDates(UUID employeeId, List<LocalDate> dates) {
        if (dates.isEmpty()) {
            return;
        }
        LocalDate from = dates.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate to = dates.stream().max(LocalDate::compareTo).orElseThrow();
        recalculate(employeeId, from, to);

        // A change in a closed month is not applied automatically; time admins decide whether to reopen it.
        Set<YearMonth> affected = dates.stream().map(YearMonth::from).filter(closedMonths()::contains)
                .collect(Collectors.toCollection(TreeSet::new));
        if (!affected.isEmpty()) {
            String name = employees.findEmployee(employeeId).map(EmployeeDirectory.EmployeeSummary::displayName)
                    .orElse("An employee");
            persons.activeEmployeesWithRole(Role.TIME_ADMIN).forEach(admin -> notifications.notify(admin,
                    NotificationType.TIME_MONTH_CLOSED_CHANGED, "TimeMonthClosing", null,
                    "Absence change in a closed month",
                    name + "'s absence changed in closed month(s) " + affected
                            + ". Reopen the month to apply it to the time account."));
        }
    }
}
