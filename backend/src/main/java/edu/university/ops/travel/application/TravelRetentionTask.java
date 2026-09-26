package edu.university.ops.travel.application;

import edu.university.ops.shared.documents.DocumentService;
import edu.university.ops.shared.retention.RetentionProperties;
import edu.university.ops.shared.retention.RetentionTask;
import edu.university.ops.shared.workflow.WorkflowService;
import edu.university.ops.travel.domain.TravelPorts.TravelRequestRepository;
import edu.university.ops.travel.domain.TravelRequest;
import edu.university.ops.travel.domain.TravelStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Travel retention: finished trips (settled, rejected, cancelled) that ended more
 * than {@code travel-days} ago lose comments, expense descriptions, receipts and
 * decision comments. Accounting data stays.
 */
@Component
class TravelRetentionTask implements RetentionTask {

    static final EnumSet<TravelStatus> FINISHED =
            EnumSet.of(TravelStatus.SETTLED, TravelStatus.REJECTED, TravelStatus.CANCELLED);

    private final TravelRequestRepository requests;
    private final DocumentService documents;
    private final WorkflowService workflow;
    private final Clock clock;

    TravelRetentionTask(TravelRequestRepository requests, DocumentService documents, WorkflowService workflow,
                        Clock clock) {
        this.requests = requests;
        this.documents = documents;
        this.workflow = workflow;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "travel-requests";
    }

    @Override
    @Transactional
    public int apply(LocalDate today, RetentionProperties properties) {
        Instant now = Instant.now(clock);
        Instant cutoff = today.minusDays(properties.travelDays()).atStartOfDay(clock.getZone()).toInstant();
        int count = 0;
        for (TravelRequest r : requests.findByStatusInAndEndDateTimeBeforeAndAnonymisedAtIsNull(FINISHED, cutoff)) {
            // Receipt links first: travel_expense references the document rows.
            r.anonymise(now);
            requests.save(r);
            documents.deleteAll(TravelService.BUSINESS_OBJECT_TYPE, r.getId());
            workflow.removeDecisionComments(TravelService.BUSINESS_OBJECT_TYPE, r.getId());
            count++;
        }
        return count;
    }
}
