package edu.university.ops.shared.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.ops.shared.audit.AuditService;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Enqueues external exports in the caller's transaction (transactional outbox). */
@Service
public class OutboxService {

    private final OutboxEventRepository events;
    private final ObjectMapper json;
    private final AuditService audit;
    private final Clock clock;

    OutboxService(OutboxEventRepository events, ObjectMapper json, AuditService audit, Clock clock) {
        this.events = events;
        this.json = json;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Must run inside the business transaction. The idempotency key makes enqueueing
     * itself idempotent: a second enqueue with the same key is ignored.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String eventType, String aggregateType, UUID aggregateId, String idempotencyKey,
                        Map<String, Object> payload) {
        if (events.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }
        events.save(new OutboxEvent(eventType, aggregateType, aggregateId, idempotencyKey, json.valueToTree(payload),
                Instant.now(clock)));
    }

    /** Manual retry by an administrator (AGENT.md §39). */
    @Transactional
    public OutboxEvent retry(UUID eventId) {
        OutboxEvent event = events.findById(eventId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Outbox event"));
        if (event.getStatus() == OutboxEvent.Status.PROCESSED) {
            throw new BusinessException(ErrorCode.INVALID_WORKFLOW_STATE, "This export was already delivered.");
        }
        event.retryNow(Instant.now(clock));
        audit.record("INTEGRATION_RETRY_REQUESTED", "OutboxEvent", eventId, Map.of("status", event.getStatus()),
                Map.of("attempts", event.getAttempts()));
        return event;
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> eventsOf(UUID aggregateId) {
        return events.findByAggregateIdOrderByCreatedAt(aggregateId);
    }

    @Transactional(readOnly = true)
    public long count(OutboxEvent.Status status) {
        return events.countByStatus(status);
    }
}
