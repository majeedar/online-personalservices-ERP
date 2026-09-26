package edu.university.ops.time.demo;

import edu.university.ops.absence.AbsenceLookup;
import edu.university.ops.calendar.HolidayCalendar;
import edu.university.ops.employee.EmployeeDirectory;
import edu.university.ops.shared.configuration.OpsProperties;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.security.RunAs;
import edu.university.ops.time.application.TimeClockService;
import edu.university.ops.time.application.TimeCorrectionService;
import edu.university.ops.time.domain.TimeCorrectionRequest.Operation;
import edu.university.ops.time.domain.TimeEntry.Source;
import edu.university.ops.time.domain.TimeEntry.Type;
import edu.university.ops.time.domain.TimeRepositories.TimeCorrectionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * DEMO DATA ONLY (ADR-009). Two weeks of terminal bookings for a few employees,
 * one day with a missing clock-out for the live demo (Scenario 6), and one
 * pending correction. Runs once, after the absence demo data.
 */
@Component
@Profile("demo")
@Order(20)
class TimeDemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TimeDemoData.class);

    private final TimeClockService clockService;
    private final TimeCorrectionService corrections;
    private final TimeCorrectionRepository correctionRepository;
    private final EmployeeDirectory employees;
    private final HolidayCalendar holidays;
    private final AbsenceLookup absences;
    private final RunAs runAs;
    private final OpsProperties properties;
    private final Clock clock;

    TimeDemoData(TimeClockService clockService, TimeCorrectionService corrections,
                 TimeCorrectionRepository correctionRepository, EmployeeDirectory employees, HolidayCalendar holidays,
                 AbsenceLookup absences, RunAs runAs, OpsProperties properties, Clock clock) {
        this.clockService = clockService;
        this.corrections = corrections;
        this.correctionRepository = correctionRepository;
        this.employees = employees;
        this.holidays = holidays;
        this.absences = absences;
        this.runAs = runAs;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (correctionRepository.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate employeeGap = previousWorkingDay("employee", today.minusDays(1));
        LocalDate partTimeGap = previousWorkingDay("parttime", today.minusDays(2));
        for (String username : new String[] {"employee", "parttime", "cbeispiel", "supervisor"}) {
            UUID id = runAs.principal(username).employeeId();
            int variant = 0;
            for (LocalDate d = today.minusDays(14); d.isBefore(today); d = d.plusDays(1)) {
                if (!isWorkingDay(id, d)) {
                    continue;
                }
                boolean missingClockOut = ("employee".equals(username) && d.equals(employeeGap))
                        || ("parttime".equals(username) && d.equals(partTimeGap));
                book(id, d, variant++, missingClockOut);
            }
        }
        // Pending correction (seed requirement AGENT.md §56) for the part-time employee's gap.
        try {
            OpsPrincipal partTime = runAs.principal("parttime");
            runAs.call(partTime, () -> corrections.request(partTime, new TimeCorrectionService.CorrectionInput(
                    partTimeGap, Operation.ADD, null, LocalTime.of(16, 30), Type.CLOCK_OUT,
                    "Forgot to clock out after the project meeting.")));
        } catch (RuntimeException e) {
            log.warn("Demo time correction skipped: {}", e.getMessage());
        }
        log.info("Demo time entries created");
    }

    private void book(UUID employeeId, LocalDate date, int variant, boolean missingClockOut) {
        int startMinute = 7 * 60 + 45 + (variant % 4) * 10;
        int workMinutes = (employees.workScheduleOn(employeeId, date)
                .map(s -> s.targetMinutesOn(date.getDayOfWeek())).orElse(480)) + (variant % 3 - 1) * 20;
        try {
            record(employeeId, date, startMinute, Type.CLOCK_IN);
            if (workMinutes > 360) {
                record(employeeId, date, 12 * 60, Type.BREAK_START);
                record(employeeId, date, 12 * 60 + 30, Type.BREAK_END);
            }
            if (!missingClockOut) {
                int end = startMinute + workMinutes + (workMinutes > 360 ? 30 : 0);
                record(employeeId, date, end, Type.CLOCK_OUT);
            }
        } catch (RuntimeException e) {
            log.warn("Demo booking {} {} skipped: {}", employeeId, date, e.getMessage());
        }
    }

    private void record(UUID employeeId, LocalDate date, int minuteOfDay, Type type) {
        var at = ZonedDateTime.of(date, LocalTime.of(minuteOfDay / 60, minuteOfDay % 60), properties.timezone());
        clockService.record(employeeId, at.toInstant(), type, Source.MOCK_TERMINAL);
    }

    private boolean isWorkingDay(UUID employeeId, LocalDate date) {
        boolean scheduled = employees.workScheduleOn(employeeId, date)
                .map(s -> s.isWorkingDay(date.getDayOfWeek())).orElse(false);
        return scheduled && !holidays.isHoliday(date) && absences.approvedAbsences(employeeId, date, date).isEmpty();
    }

    private LocalDate previousWorkingDay(String username, LocalDate from) {
        UUID id = runAs.principal(username).employeeId();
        LocalDate d = from;
        while (!isWorkingDay(id, d)) {
            d = d.minusDays(1);
        }
        return d;
    }
}
