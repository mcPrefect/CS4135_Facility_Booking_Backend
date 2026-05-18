package com.facilitybooking.bookingservice.infrastructure.messaging;

import com.facilitybooking.bookingservice.domain.BookingDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publishes Booking domain events to the RabbitMQ booking.events exchange.
 * Consumed by: Notification Service and Approval Service.
 */
@Component
public class BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchanges.booking-events:booking.events}")
    private String exchange;

    public BookingEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(BookingDomainEvent event) {
        String routingKey = "booking." + event.eventType().toLowerCase();
        log.info("Publishing domain event: type={} bookingId={} routingKey={}",
                event.eventType(), event.bookingId(), routingKey);
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
    }
}
