package com.facilitybooking.notification.infrastructure.email;

import com.facilitybooking.notification.domain.Notification;
import com.facilitybooking.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailDispatchService {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatchService.class);

    private final JavaMailSender         mailSender;
    private final NotificationRepository notificationRepository;

    public EmailDispatchService(JavaMailSender mailSender, NotificationRepository repo) {
        this.mailSender             = mailSender;
        this.notificationRepository = repo;
    }

    public void send(Notification notification) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(notification.getRecipientId() + "@plassey.ie"); // resolved via User Service in prod
            msg.setSubject("Plassey Planner: " + notification.getType().name().replace('_', ' '));
            msg.setText(notification.getMessage());
            mailSender.send(msg);

            notification.markSent();
            notificationRepository.save(notification);
            log.info("Email sent for notificationId={}", notification.getNotificationId());

        } catch (MailException e) {
            notification.markFailed(e.getMessage());
            notificationRepository.save(notification);
            log.error("Email dispatch failed for notificationId={}: {}", notification.getNotificationId(), e.getMessage());
            throw e; // rethrow so circuit breaker counts it
        }
    }
}
