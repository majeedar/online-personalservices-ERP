package edu.university.ops.shared.notification;

import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central notification entry point (AGENT.md §19). A notification is stored in
 * the caller's transaction; mail delivery happens after commit through the event
 * publication registry, so a crash never loses a mail and a rollback never sends one.
 */
@Service
@Transactional
public class NotificationService {

    /** Published when a notification row is stored; the dispatcher sends the mail after commit. */
    public record NotificationCreated(UUID notificationId) {
    }

    private final NotificationRepository notifications;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    NotificationService(NotificationRepository notifications, ApplicationEventPublisher events, Clock clock) {
        this.notifications = notifications;
        this.events = events;
        this.clock = clock;
    }

    public UUID notify(UUID recipientId, NotificationType type, String businessObjectType, UUID businessObjectId,
                       String subject, String message) {
        Notification n = notifications.save(new Notification(recipientId, type, businessObjectType, businessObjectId,
                subject, message, Instant.now(clock)));
        events.publishEvent(new NotificationCreated(n.getId()));
        return n.getId();
    }

    @Transactional(readOnly = true)
    public List<Notification> latest(UUID recipientId, int limit) {
        return notifications.findByRecipientEmployeeIdOrderByCreatedAtDesc(recipientId, Limit.of(limit));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return notifications.countByRecipientEmployeeIdAndReadAtIsNull(recipientId);
    }

    public void markRead(UUID notificationId, UUID recipientId) {
        Notification n = notifications.findById(notificationId)
                .filter(x -> x.getRecipientEmployeeId().equals(recipientId))
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Notification"));
        n.markRead(Instant.now(clock));
    }

    public void markAllRead(UUID recipientId) {
        Instant now = Instant.now(clock);
        notifications.findByRecipientEmployeeIdAndReadAtIsNull(recipientId).forEach(n -> n.markRead(now));
    }

    @Transactional(readOnly = true)
    public boolean alreadyNotifiedSince(UUID recipientId, NotificationType type, UUID businessObjectId,
                                        Instant since) {
        return notifications.existsByRecipientEmployeeIdAndTypeAndBusinessObjectIdAndCreatedAtAfter(recipientId, type,
                businessObjectId, since);
    }

    /** Retention: deletes notifications created before the start of {@code day}; returns the count. */
    public int deleteCreatedBefore(java.time.LocalDate day) {
        return notifications.deleteByCreatedAtBefore(day.atStartOfDay(clock.getZone()).toInstant());
    }

    @Transactional(readOnly = true)
    public long failedDeliveries() {
        return notifications.countByStatus(Notification.DeliveryStatus.FAILED);
    }
}
