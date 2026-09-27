package edu.university.ops.shared.integration;

import edu.university.ops.shared.i18n.Text;
import edu.university.ops.shared.directory.PersonDirectory;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import edu.university.ops.shared.monitoring.CorrelationId;
import edu.university.ops.shared.notification.NotificationService;
import edu.university.ops.shared.notification.NotificationType;
import edu.university.ops.shared.security.Role;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records integration runs and errors (AGENT.md §26). Writes use their own
 * transactions, so a failure is recorded even when the business transaction
 * that triggered the call rolls back.
 */
@Service
public class IntegrationMonitor {

    private final IntegrationRunRepository runs;
    private final IntegrationErrorRepository errors;
    private final NotificationService notifications;
    private final PersonDirectory persons;
    private final MeterRegistry meters;
    private final Clock clock;

    IntegrationMonitor(IntegrationRunRepository runs, IntegrationErrorRepository errors,
                       NotificationService notifications, PersonDirectory persons, MeterRegistry meters, Clock clock) {
        this.runs = runs;
        this.errors = errors;
        this.notifications = notifications;
        this.persons = persons;
        this.meters = meters;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID startRun(String interfaceName, IntegrationRun.Trigger trigger) {
        return runs.save(new IntegrationRun(interfaceName, trigger, Instant.now(clock), CorrelationId.current()))
                .getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID recordError(UUID runId, String externalReference, String errorCode, String message, int retryCount,
                            UUID outboxEventId) {
        return errors.save(new IntegrationError(runId, externalReference, errorCode, message, retryCount,
                outboxEventId, Instant.now(clock))).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishRun(UUID runId, int read, int written, int failed) {
        IntegrationRun run = runs.findById(runId).orElseThrow();
        run.finish(read, written, failed, Instant.now(clock));
        meters.counter("ops.integration.runs", "interface", run.getInterfaceName(), "status", run.getStatus().name())
                .increment();
    }

    /** Alerts ERP admins in-app and by mail (AGENT.md §26 "Alert ERP_ADMIN"). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void alertAdmins(Text subject, Text message, String businessObjectType, UUID businessObjectId) {
        persons.activeEmployeesWithRole(Role.ERP_ADMIN).forEach(admin -> notifications.notify(admin,
                NotificationType.INTEGRATION_FAILURE, businessObjectType, businessObjectId, subject, message));
    }

    @Transactional
    public void resolveErrorsOf(UUID outboxEventId) {
        Instant now = Instant.now(clock);
        errors.findByOutboxEventIdAndResolvedFalse(outboxEventId).forEach(e -> e.resolve(now));
    }

    @Transactional
    public IntegrationError resolve(UUID errorId) {
        IntegrationError error = errors.findById(errorId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Integration error"));
        error.resolve(Instant.now(clock));
        return error;
    }

    @Transactional(readOnly = true)
    public IntegrationError error(UUID errorId) {
        return errors.findById(errorId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Integration error"));
    }

    @Transactional(readOnly = true)
    public Page<IntegrationRun> runs(Pageable pageable) {
        return runs.findAllByOrderByStartedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<IntegrationError> errors(boolean openOnly, Pageable pageable) {
        return openOnly ? errors.findByResolvedFalseOrderByCreatedAtDesc(pageable)
                : errors.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public long openErrorCount() {
        return errors.countByResolvedFalse();
    }
}
