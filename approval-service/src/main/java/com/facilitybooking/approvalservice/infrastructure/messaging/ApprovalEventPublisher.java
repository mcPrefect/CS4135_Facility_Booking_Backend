package com.facilitybooking.approvalservice.infrastructure.messaging;

import com.facilitybooking.approvalservice.domain.ApprovalDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publishes approval decisions to the booking.events exchange.
 * Consumed by: Booking Service (updates booking state),
 *              Notification Service (notifies user).
 */
@Component
public class ApprovalEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ApprovalEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchanges.booking-events:booking.events}")
    private String exchange;

    public ApprovalEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(ApprovalDomainEvent event) {
        String routingKey = "booking.approval." + event.eventType().toLowerCase();
        log.info("Publishing approval event: type={} bookingId={} routingKey={}",
                event.eventType(), event.bookingId(), routingKey);
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
    }
}
