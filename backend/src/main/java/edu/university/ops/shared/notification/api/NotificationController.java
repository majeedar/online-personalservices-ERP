package edu.university.ops.shared.notification.api;

import edu.university.ops.shared.notification.Notification;
import edu.university.ops.shared.notification.NotificationService;
import edu.university.ops.shared.notification.NotificationType;
import edu.university.ops.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
class NotificationController {

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    record NotificationResponse(UUID id, NotificationType type, String businessObjectType, UUID businessObjectId,
                                String subject, String message, Instant createdAt, boolean read) {
        static NotificationResponse of(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getBusinessObjectType(), n.getBusinessObjectId(),
                    n.subjectText().render(), n.messageText().render(), n.getCreatedAt(), n.getReadAt() != null);
        }
    }

    record UnreadCount(long unread) {
    }

    @GetMapping
    @Operation(summary = "The caller's latest notifications")
    List<NotificationResponse> list(@RequestParam(defaultValue = "20") int limit) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return notifications.latest(CurrentUser.require().employeeId(), bounded).stream()
                .map(NotificationResponse::of).toList();
    }

    @GetMapping("/unread-count")
    UnreadCount unread() {
        return new UnreadCount(notifications.unreadCount(CurrentUser.require().employeeId()));
    }

    @PostMapping("/{id}/read")
    ResponseEntity<Void> read(@PathVariable UUID id) {
        notifications.markRead(id, CurrentUser.require().employeeId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    ResponseEntity<Void> readAll() {
        notifications.markAllRead(CurrentUser.require().employeeId());
        return ResponseEntity.noContent().build();
    }
}
