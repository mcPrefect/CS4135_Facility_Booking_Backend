package com.plassey.notification.application;

import com.plassey.notification.domain.Notification;
import com.plassey.notification.domain.NotificationStatus;
import com.plassey.notification.infrastructure.email.EmailDispatchService;
import com.plassey.notification.repository.NotificationRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final EmailDispatchService   emailDispatchService;

    public NotificationService(NotificationRepository repo, EmailDispatchService emailDispatchService) {
        this.notificationRepository = repo;
        this.emailDispatchService   = emailDispatchService;
    }

    @Transactional
    public void createAndDispatch(Notification notification) {
        // Idempotency check: skip if already exists for same recipient + type
        boolean duplicate = notificationRepository.existsByRecipientIdAndTypeAndStatus(
                notification.getRecipientId(), notification.getType(), NotificationStatus.SENT);
        if (duplicate) {
            log.info("Idempotency: Notification already SENT for recipientId={} type={} – skipping.",
                    notification.getRecipientId(), notification.getType());
            return;
        }

        notificationRepository.save(notification);
        dispatch(notification);
    }

    @CircuitBreaker(name = "emailDispatch", fallbackMethod = "fallbackToInApp")
    void dispatch(Notification notification) {
        switch (notification.getChannel()) {
            case EMAIL -> emailDispatchService.send(notification);
            case IN_APP -> {
                notification.markSent();
                notificationRepository.save(notification);
                log.info("IN_APP notification marked SENT: notificationId={}", notification.getNotificationId());
            }
        }
    }

    // Fallback: downgrade to IN_APP when email circuit is open
    void fallbackToInApp(Notification notification, Throwable ex) {
        log.warn("emailDispatch circuit open – falling back to IN_APP for notificationId={}. Reason: {}",
                notification.getNotificationId(), ex.getMessage());
        notification.markSent(); // persist as sent via IN_APP
        notificationRepository.save(notification);
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
