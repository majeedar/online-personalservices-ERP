package edu.university.ops.shared.retention;

import edu.university.ops.shared.notification.NotificationService;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** Deletes notifications older than {@code ops.retention.notification-days}. */
@Component
class NotificationRetentionTask implements RetentionTask {

    private final NotificationService notifications;

    NotificationRetentionTask(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public String name() {
        return "notifications";
    }

    @Override
    public int apply(LocalDate today, RetentionProperties properties) {
        return notifications.deleteCreatedBefore(today.minusDays(properties.notificationDays()));
    }
}
