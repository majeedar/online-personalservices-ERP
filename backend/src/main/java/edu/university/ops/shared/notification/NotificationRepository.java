package edu.university.ops.shared.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.Repository;

interface NotificationRepository extends Repository<Notification, UUID> {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    List<Notification> findByRecipientEmployeeIdOrderByCreatedAtDesc(UUID recipientId, Limit limit);

    List<Notification> findByRecipientEmployeeIdAndReadAtIsNull(UUID recipientId);

    long countByRecipientEmployeeIdAndReadAtIsNull(UUID recipientId);

    long countByStatus(Notification.DeliveryStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from Notification n where n.createdAt < :before")
    int deleteByCreatedAtBefore(@org.springframework.data.repository.query.Param("before") java.time.Instant before);

    boolean existsByRecipientEmployeeIdAndTypeAndBusinessObjectIdAndCreatedAtAfter(UUID recipientId,
                                                                                 NotificationType type,
                                                                                 UUID businessObjectId,
                                                                                 java.time.Instant after);
}
