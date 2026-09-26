package edu.university.ops.time.application;

import edu.university.ops.employee.EmployeeDirectory;
import edu.university.ops.shared.batch.BatchJob;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Nightly recalculation of recent time-account days (AGENT.md §27.6). Also
 * catches days nobody booked on (absent without request), which then show their
 * full negative balance.
 */
@Component
class TimeAccountRecalculationJob implements BatchJob {

    static final int DAYS_BACK = 7;

    private final EmployeeDirectory employees;
    private final TimeAccountService accounts;
    private final Clock clock;

    TimeAccountRecalculationJob(EmployeeDirectory employees, TimeAccountService accounts, Clock clock) {
        this.employees = employees;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "time-account-recalculation";
    }

    @Override
    public String description() {
        return "Recalculate the daily time accounts of the last " + DAYS_BACK + " days";
    }

    @Override
    public String defaultCron() {
        return "0 0 1 * * *";
    }

    @Override
    public void run(Context context) {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        for (var employee : employees.activeEmployees()) {
            try {
                accounts.recalculate(employee.id(), yesterday.minusDays(DAYS_BACK - 1L), yesterday);
                context.success();
            } catch (RuntimeException e) {
                context.failure(employee.personnelNumber(), "RECALCULATION_FAILED", e.getMessage());
            }
        }
    }
}
