package edu.university.ops.time.application;

import edu.university.ops.shared.retention.RetentionProperties;
import edu.university.ops.shared.retention.RetentionTask;
import edu.university.ops.shared.workflow.WorkflowService;
import edu.university.ops.time.domain.TimeCorrectionRequest;
import edu.university.ops.time.domain.TimeRepositories.TimeCorrectionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Time-correction retention: decided corrections older than {@code time-correction-days} lose their reason. */
@Component
class TimeCorrectionRetentionTask implements RetentionTask {

    private final TimeCorrectionRepository corrections;
    private final WorkflowService workflow;
    private final Clock clock;

    TimeCorrectionRetentionTask(TimeCorrectionRepository corrections, WorkflowService workflow, Clock clock) {
        this.corrections = corrections;
        this.workflow = workflow;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "time-corrections";
    }

    @Override
    @Transactional
    public int apply(LocalDate today, RetentionProperties properties) {
        Instant now = Instant.now(clock);
        int count = 0;
        for (TimeCorrectionRequest c : corrections.findByStatusInAndDateBeforeAndAnonymisedAtIsNull(
                EnumSet.of(TimeCorrectionRequest.Status.APPROVED, TimeCorrectionRequest.Status.REJECTED,
                        TimeCorrectionRequest.Status.CANCELLED), today.minusDays(properties.timeCorrectionDays()))) {
            workflow.removeDecisionComments(TimeCorrectionService.BUSINESS_OBJECT_TYPE, c.getId());
            c.anonymise(now);
            count++;
        }
        return count;
    }
}
