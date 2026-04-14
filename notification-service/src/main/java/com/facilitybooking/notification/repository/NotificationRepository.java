package com.facilitybooking.notification.repository;

import com.facilitybooking.notification.domain.Notification;
import com.facilitybooking.notification.domain.NotificationStatus;
import com.facilitybooking.notification.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
    long countByRecipientIdAndIsReadFalse(UUID recipientId);
    Optional<Notification> findByRecipientIdAndType(UUID recipientId, NotificationType type);
    boolean existsByRecipientIdAndTypeAndStatus(UUID recipientId, NotificationType type, NotificationStatus status);
}
