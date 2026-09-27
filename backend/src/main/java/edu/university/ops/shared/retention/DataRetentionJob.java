package edu.university.ops.shared.retention;

import edu.university.ops.shared.i18n.Text;
import edu.university.ops.shared.audit.AuditService;
import edu.university.ops.shared.batch.BatchJob;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Data retention (AGENT.md §82): removes personal free text, attachments and old
 * notifications that are no longer needed. The audit trail is kept; the run is
 * audited with counts only.
 */
@Component
@EnableConfigurationProperties(RetentionProperties.class)
class DataRetentionJob implements BatchJob {

    private final List<RetentionTask> tasks;
    private final RetentionProperties properties;
    private final AuditService audit;
    private final Clock clock;

    DataRetentionJob(List<RetentionTask> tasks, RetentionProperties properties, AuditService audit, Clock clock) {
        this.tasks = tasks;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "data-retention";
    }

    @Override
    public Text description() {
        return Text.of("Anonymise finished requests and delete old notifications after their retention period");
    }

    @Override
    public String defaultCron() {
        return "0 30 3 * * SUN";
    }

    @Override
    public void run(Context context) {
        LocalDate today = LocalDate.now(clock);
        Map<String, Object> counts = new LinkedHashMap<>();
        for (RetentionTask task : tasks) {
            try {
                int n = task.apply(today, properties);
                counts.put(task.name(), n);
                context.successes(n);
            } catch (RuntimeException e) {
                context.failure(task.name(), "RETENTION_FAILED", e.getMessage());
            }
        }
        audit.record("DATA_RETENTION_APPLIED", "DataRetention", today, null, counts);
    }
}
