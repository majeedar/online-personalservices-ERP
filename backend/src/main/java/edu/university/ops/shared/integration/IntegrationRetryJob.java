package edu.university.ops.shared.integration;

import edu.university.ops.shared.batch.BatchJob;
import org.springframework.stereotype.Component;

/**
 * Integration retry (AGENT.md §27.8): delivers every outbox export whose backoff
 * has elapsed. The outbox processor also runs continuously; this job makes the
 * retries visible in the batch history and can be started manually.
 */
@Component
class IntegrationRetryJob implements BatchJob {

    private final OutboxProcessor processor;
    private final OutboxService outbox;

    IntegrationRetryJob(OutboxProcessor processor, OutboxService outbox) {
        this.processor = processor;
        this.outbox = outbox;
    }

    @Override
    public String name() {
        return "integration-retry";
    }

    @Override
    public String description() {
        return "Retry pending external exports (travel ERP, finance postings)";
    }

    @Override
    public String defaultCron() {
        return "0 */15 * * * *";
    }

    @Override
    public void run(Context context) {
        long pendingBefore = outbox.count(OutboxEvent.Status.PENDING);
        int delivered = processor.processDue(IntegrationRun.Trigger.RETRY);
        context.successes(delivered);
        long stillPending = outbox.count(OutboxEvent.Status.PENDING);
        if (stillPending > 0 && delivered < pendingBefore) {
            context.failure("outbox", "EXPORTS_PENDING", stillPending + " export(s) still pending after retry");
        }
    }
}
