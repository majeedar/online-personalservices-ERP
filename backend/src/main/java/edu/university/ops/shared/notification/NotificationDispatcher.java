package edu.university.ops.shared.notification;

import edu.university.ops.shared.directory.PersonDirectory;
import edu.university.ops.shared.monitoring.CorrelationId;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import edu.university.ops.shared.i18n.Translator;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Delivers stored notifications through the {@link NotificationGateway} after the
 * originating transaction committed. A delivery failure never affects the
 * business transaction; it is recorded on the notification and counted
 * ({@code ops.notifications.delivery}).
 */
@Component
class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationRepository notifications;
    private final ObjectProvider<NotificationGateway> gateway;
    private final PersonDirectory persons;
    private final MeterRegistry meters;
    private final Clock clock;

    NotificationDispatcher(NotificationRepository notifications, ObjectProvider<NotificationGateway> gateway,
                           PersonDirectory persons, MeterRegistry meters, Clock clock) {
        this.notifications = notifications;
        this.gateway = gateway;
        this.persons = persons;
        this.meters = meters;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(NotificationService.NotificationCreated event) {
        CorrelationId.startNew();
        try {
            notifications.findById(event.notificationId()).ifPresent(this::deliver);
        } finally {
            CorrelationId.clear();
        }
    }

    private void deliver(Notification n) {
        NotificationGateway channel = gateway.getIfAvailable();
        var person = persons.findPerson(n.getRecipientEmployeeId());
        if (channel == null || person.isEmpty() || !person.get().active()) {
            n.markSkipped();
            count("skipped");
            return;
        }
        try {
            Locale language = Translator.forLanguageCode(person.get().language());
            channel.send(new NotificationGateway.NotificationMessage(person.get().email(), person.get().displayName(),
                    n.subjectText().render(language), n.messageText().render(language)));
            n.markSent(Instant.now(clock));
            count("sent");
        } catch (RuntimeException e) {
            // Logged without message body or address (data protection, AGENT.md §52).
            log.warn("Notification delivery failed: notification={} type={} error={}", n.getId(), n.getType(),
                    e.getClass().getSimpleName());
            n.markFailed();
            count("failed");
        }
    }

    private void count(String result) {
        meters.counter("ops.notifications.delivery", "result", result).increment();
    }
}
