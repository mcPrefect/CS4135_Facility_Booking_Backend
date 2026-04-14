package com.facilitybooking.notification.application;

import com.facilitybooking.notification.domain.Notification;
import com.facilitybooking.notification.infrastructure.email.EmailDispatchService;
import com.facilitybooking.notification.repository.NotificationRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Separate Spring bean so that @CircuitBreaker is applied via AOP proxy.
 * Self-invocation within the same bean bypasses the proxy, so dispatch
 * must live here and be called from NotificationService.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final EmailDispatchService   emailDispatchService;
    private final NotificationRepository notificationRepository;

    public NotificationDispatchService(EmailDispatchService emailDispatchService,
                                       NotificationRepository notificationRepository) {
        this.emailDispatchService   = emailDispatchService;
        this.notificationRepository = notificationRepository;
    }

    @CircuitBreaker(name = "emailDispatch", fallbackMethod = "fallbackToInApp")
    @Transactional
    public void dispatch(Notification notification) {
        switch (notification.getChannel()) {
            case EMAIL -> emailDispatchService.send(notification);
            case IN_APP -> {
                notification.markSent();
                notificationRepository.save(notification);
                log.info("IN_APP notification marked SENT: notificationId={}", notification.getNotificationId());
            }
        }
    }

    // Resilience4j calls this when emailDispatch circuit is OPEN
    @Transactional
    public void fallbackToInApp(Notification notification, Throwable ex) {
        log.warn("emailDispatch circuit open – falling back to IN_APP for notificationId={}. Reason: {}",
                notification.getNotificationId(), ex.getMessage());
        notification.markSent();
        notificationRepository.save(notification);
    }
}
