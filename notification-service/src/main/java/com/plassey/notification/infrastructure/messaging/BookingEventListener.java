package com.plassey.notification.infrastructure.messaging;

import com.plassey.notification.application.NotificationService;
import com.plassey.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);

    private final BookingEventTranslator translator;
    private final NotificationService    notificationService;

    public BookingEventListener(BookingEventTranslator translator, NotificationService notificationService) {
        this.translator          = translator;
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "${rabbitmq.queues.booking-events}")
    public void handleBookingEvent(BookingEventDto dto) {
        log.info("Received booking event: type={} bookingId={}", dto.getEventType(), dto.getBookingId());

        translator.translate(dto).ifPresentOrElse(
            notificationService::createAndDispatch,
            () -> log.warn("Event discarded by ACL: eventType={}", dto.getEventType())
        );
    }
}
