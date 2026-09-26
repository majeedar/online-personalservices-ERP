package edu.university.ops.time.application;

import edu.university.ops.shared.batch.BatchJob;
import edu.university.ops.shared.exception.BusinessException;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.stereotype.Component;

/**
 * Closes the previous month's time accounts on the 10th, giving employees and
 * approvers time for corrections. If corrections are still pending the run ends
 * FAILED with the reason, which alerts the administrators; it can be run again
 * once they are decided. Idempotent: an already closed month is a success.
 */
@Component
class TimeMonthClosingJob implements BatchJob {

    private final TimeClosingService closings;
    private final Clock clock;

    TimeMonthClosingJob(TimeClosingService closings, Clock clock) {
        this.closings = closings;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "time-month-closing";
    }

    @Override
    public String description() {
        return "Close the previous month's time accounts";
    }

    @Override
    public String defaultCron() {
        return "0 0 4 10 * *";
    }

    @Override
    public void run(Context context) {
        YearMonth previous = YearMonth.now(clock).minusMonths(1);
        if (closings.isClosed(previous)) {
            context.success();
            return;
        }
        try {
            closings.close(previous, "scheduler");
            context.success();
        } catch (BusinessException e) {
            context.failure(previous.toString(), e.code().name(), e.getMessage());
        }
    }
}
