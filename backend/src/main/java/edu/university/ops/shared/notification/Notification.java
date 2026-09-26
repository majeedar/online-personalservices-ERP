package edu.university.ops.shared.notification;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One notification. The row is the in-app channel; {@code status} tracks the
 * mail channel (PENDING → SENT / FAILED, or SKIPPED when mail is disabled).
 */
@Entity
@Table(name = "notification")
public class Notification {

    public enum DeliveryStatus { PENDING, SENT, FAILED, SKIPPED }

    @Id
    private UUID id;

    private UUID recipientEmployeeId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private String businessObjectType;
    private UUID businessObjectId;
    private String subject;
    private String message;

    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;

    private Instant createdAt;
    private Instant sentAt;
    private Instant readAt;

    protected Notification() {
    }

    Notification(UUID recipientEmployeeId, NotificationType type, String businessObjectType, UUID businessObjectId,
                 String subject, String message, Instant now) {
        this.id = UUID.randomUUID();
        this.recipientEmployeeId = recipientEmployeeId;
        this.type = type;
        this.businessObjectType = businessObjectType;
        this.businessObjectId = businessObjectId;
        this.subject = subject;
        this.message = message;
        this.status = DeliveryStatus.PENDING;
        this.createdAt = now;
    }

    void markSent(Instant now) {
        this.status = DeliveryStatus.SENT;
        this.sentAt = now;
    }

    void markFailed() {
        this.status = DeliveryStatus.FAILED;
    }

    void markSkipped() {
        this.status = DeliveryStatus.SKIPPED;
    }

    void markRead(Instant now) {
        if (readAt == null) {
            this.readAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecipientEmployeeId() {
        return recipientEmployeeId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getBusinessObjectType() {
        return businessObjectType;
    }

    public UUID getBusinessObjectId() {
        return businessObjectId;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
