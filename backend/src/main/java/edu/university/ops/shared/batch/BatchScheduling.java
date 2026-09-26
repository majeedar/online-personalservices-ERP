package edu.university.ops.shared.batch;

import edu.university.ops.shared.configuration.OpsProperties;
import edu.university.ops.shared.exception.BusinessException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

/**
 * Registers each job's cron schedule (AGENT.md §48 "batch schedules"). A schedule
 * of "-" disables a job's timer; it can still be started from the admin screen.
 */
@Configuration
@EnableConfigurationProperties(BatchScheduling.BatchProperties.class)
class BatchScheduling implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(BatchScheduling.class);

    @ConfigurationProperties(prefix = "ops.batch")
    record BatchProperties(@DefaultValue("true") boolean enabled, Map<String, String> schedules) {
    }

    private final BatchJobService service;
    private final BatchProperties properties;
    private final OpsProperties opsProperties;

    BatchScheduling(BatchJobService service, BatchProperties properties, OpsProperties opsProperties) {
        this.service = service;
        this.properties = properties;
        this.opsProperties = opsProperties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (!properties.enabled()) {
            log.info("Scheduled batch jobs are disabled (ops.batch.enabled=false)");
            return;
        }
        for (BatchJob job : service.jobs()) {
            String cron = properties.schedules() != null && properties.schedules().containsKey(job.name())
                    ? properties.schedules().get(job.name()) : job.defaultCron();
            if (cron == null || cron.isBlank() || "-".equals(cron)) {
                continue;
            }
            registrar.addCronTask(new CronTask(() -> {
                try {
                    service.run(job.name(), BatchJobRun.Trigger.SCHEDULED, "scheduler");
                } catch (BusinessException e) {
                    log.info("Scheduled run of {} skipped: {}", job.name(), e.getMessage());
                }
            }, new CronTrigger(cron, opsProperties.timezone())));
            log.info("Batch job {} scheduled: {} ({})", job.name(), cron, opsProperties.timezone());
        }
    }
}
