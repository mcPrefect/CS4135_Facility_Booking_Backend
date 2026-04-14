package com.facilitybooking.notification.application;

import com.facilitybooking.notification.domain.Notification;
import com.facilitybooking.notification.domain.NotificationStatus;
import com.facilitybooking.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository     notificationRepository;
    private final NotificationDispatchService dispatchService;

    public NotificationService(NotificationRepository repo, NotificationDispatchService dispatchService) {
        this.notificationRepository = repo;
        this.dispatchService        = dispatchService;
    }

    @Transactional
    public void createAndDispatch(Notification notification) {
        // Idempotency check: skip if already SENT for same recipient + type
        boolean duplicate = notificationRepository.existsByRecipientIdAndTypeAndStatus(
                notification.getRecipientId(), notification.getType(), NotificationStatus.SENT);
        if (duplicate) {
            log.info("Idempotency: Notification already SENT for recipientId={} type={} – skipping.",
                    notification.getRecipientId(), notification.getType());
            return;
        }

        notificationRepository.save(notification);
        // Call through separate bean so @CircuitBreaker AOP proxy is active
        dispatchService.dispatch(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> getNotificationsForUser(UUID userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markRead(UUID notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.markRead();
            notificationRepository.save(n);
        });
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }
}
