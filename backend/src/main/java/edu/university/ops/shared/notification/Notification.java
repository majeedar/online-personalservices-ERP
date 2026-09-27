package edu.university.ops.shared.notification;

import edu.university.ops.shared.i18n.Text;
import edu.university.ops.shared.i18n.Translator;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


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

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> subjectText;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> messageText;

    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;

    private Instant createdAt;
    private Instant sentAt;
    private Instant readAt;

    protected Notification() {
    }

    Notification(UUID recipientEmployeeId, NotificationType type, String businessObjectType, UUID businessObjectId,
                 Text subject, Text message, Instant now) {
        this.id = UUID.randomUUID();
        this.recipientEmployeeId = recipientEmployeeId;
        this.type = type;
        this.businessObjectType = businessObjectType;
        this.businessObjectId = businessObjectId;
        this.subject = subject.english();
        this.message = message.english();
        this.subjectText = Translator.toMap(subject);
        this.messageText = Translator.toMap(message);
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

    /** English subject (fallback, logs). */
    public String getSubject() {
        return subject;
    }

    /** English message (fallback, logs). */
    public String getMessage() {
        return message;
    }

    /** Subject to render in the reader's language. */
    public Text subjectText() {
        return Translator.stored(subjectText, subject);
    }

    public Text messageText() {
        return Translator.stored(messageText, message);
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
