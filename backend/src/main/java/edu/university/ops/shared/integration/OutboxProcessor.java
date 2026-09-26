package edu.university.ops.shared.integration;

import edu.university.ops.shared.monitoring.CorrelationId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Delivers due outbox events (AGENT.md §50) and doubles as the integration retry
 * job (§27.8): failed deliveries are retried with backoff until
 * {@code ops.integration.outbox-max-attempts}, then marked FAILED, recorded as an
 * integration error and reported to ERP admins. Each event is delivered in its own
 * transaction under a row lock, so parallel processors never deliver it twice.
 */
@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxEventRepository events;
    private final Map<String, OutboxHandler> handlers;
    private final IntegrationMonitor monitor;
    private final IntegrationProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    OutboxProcessor(OutboxEventRepository events, List<OutboxHandler> handlers, IntegrationMonitor monitor,
                    IntegrationProperties properties, TransactionTemplate tx, Clock clock) {
        this.events = events;
        this.handlers = handlers.stream().collect(Collectors.toMap(OutboxHandler::eventType, Function.identity()));
        this.monitor = monitor;
        this.properties = properties;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${ops.integration.outbox-poll-interval:5s}",
            initialDelayString = "${ops.integration.outbox-initial-delay:10s}")
    public void scheduled() {
        processDue(IntegrationRun.Trigger.EVENT);
    }

    /** @return number of events delivered successfully */
    public int processDue(IntegrationRun.Trigger trigger) {
        int delivered = 0;
        for (UUID id : events.findDueIds(Instant.now(clock))) {
            Boolean ok = tx.execute(status -> deliver(id, trigger));
            if (Boolean.TRUE.equals(ok)) {
                delivered++;
            }
        }
        return delivered;
    }

    private Boolean deliver(UUID id, IntegrationRun.Trigger trigger) {
        OutboxEvent event = events.lockPending(id).orElse(null);
        if (event == null) {
            return null; // delivered meanwhile or locked by another processor
        }
        OutboxHandler handler = handlers.get(event.getEventType());
        if (handler == null) {
            log.error("No outbox handler for event type {}", event.getEventType());
            return false;
        }
        CorrelationId.startNew();
        IntegrationRun.Trigger effective = event.getAttempts() > 0 ? IntegrationRun.Trigger.RETRY : trigger;
        UUID runId = monitor.startRun(handler.interfaceName(), effective);
        try {
            handler.handle(event);
            event.processed(Instant.now(clock));
            monitor.finishRun(runId, 1, 1, 0);
            monitor.resolveErrorsOf(event.getId());
            return true;
        } catch (RuntimeException e) {
            String code = e instanceof ExternalSystemException ex ? ex.errorCode() : "HANDLER_ERROR";
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            boolean gaveUp = event.failed(message, properties.outboxMaxAttempts(), Instant.now(clock));
            monitor.recordError(runId, event.getIdempotencyKey(), code, message,
                    event.getAttempts() - 1, event.getId());
            monitor.finishRun(runId, 1, 0, 1);
            if (gaveUp) {
                monitor.alertAdmins("Integration failure: " + handler.interfaceName(),
                        "Export " + event.getIdempotencyKey() + " failed " + event.getAttempts()
                                + " times and needs attention in the Integration Monitor.",
                        event.getAggregateType(), event.getAggregateId());
            }
            return false;
        } finally {
            CorrelationId.clear();
        }
    }
}
