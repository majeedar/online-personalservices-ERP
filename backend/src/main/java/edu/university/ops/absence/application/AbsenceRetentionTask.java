package edu.university.ops.absence.application;

import edu.university.ops.absence.domain.AbsenceRepositories.AbsenceRequestRepository;
import edu.university.ops.absence.domain.AbsenceRequest;
import edu.university.ops.absence.domain.AbsenceStatus;
import edu.university.ops.shared.documents.DocumentService;
import edu.university.ops.shared.retention.RetentionProperties;
import edu.university.ops.shared.retention.RetentionTask;
import edu.university.ops.shared.workflow.WorkflowService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Absence retention: finished requests (approved, rejected, cancelled) whose end
 * lies more than {@code absence-days} back lose comment, representative,
 * attachments and approvers' decision comments.
 */
@Component
class AbsenceRetentionTask implements RetentionTask {

    static final EnumSet<AbsenceStatus> FINISHED =
            EnumSet.of(AbsenceStatus.APPROVED, AbsenceStatus.REJECTED, AbsenceStatus.CANCELLED);

    private final AbsenceRequestRepository requests;
    private final DocumentService documents;
    private final WorkflowService workflow;
    private final Clock clock;

    AbsenceRetentionTask(AbsenceRequestRepository requests, DocumentService documents, WorkflowService workflow,
                         Clock clock) {
        this.requests = requests;
        this.documents = documents;
        this.workflow = workflow;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "absence-requests";
    }

    @Override
    @Transactional
    public int apply(LocalDate today, RetentionProperties properties) {
        Instant now = Instant.now(clock);
        int count = 0;
        for (AbsenceRequest r : requests.findByStatusInAndEndDateBeforeAndAnonymisedAtIsNull(FINISHED,
                today.minusDays(properties.absenceDays()))) {
            documents.deleteAll(AbsenceService.BUSINESS_OBJECT_TYPE, r.getId());
            workflow.removeDecisionComments(AbsenceService.BUSINESS_OBJECT_TYPE, r.getId());
            r.anonymise(now);
            count++;
        }
        return count;
    }
}
