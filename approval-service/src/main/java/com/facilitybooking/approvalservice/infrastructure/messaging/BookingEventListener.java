package com.facilitybooking.approvalservice.infrastructure.messaging;

import com.facilitybooking.approvalservice.application.ApprovalService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumes BookingCreated events from RabbitMQ and creates approval tasks.
 * Acts as the Anti-Corruption Layer between the Booking context and Approval context.
 */
@Component
public class BookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);

    private final ApprovalService approvalService;

    public BookingEventListener(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @RabbitListener(queues = "${rabbitmq.queues.booking-created}")
    public void handleBookingCreated(BookingEventDto dto) {
        log.info("Received booking event: type={} bookingId={}", dto.getEventType(), dto.getBookingId());

        if (!"BookingCreated".equals(dto.getEventType())) {
            log.warn("ACL: Unexpected event type '{}' on booking-created queue – discarding.", dto.getEventType());
            return;
        }

        if (dto.getBookingId() == null || dto.getUserId() == null) {
            log.warn("ACL: Rejecting malformed event – missing bookingId or userId.");
            return;
        }

        approvalService.createTask(
                dto.getBookingId(),
                dto.getUserId(),
                dto.getFacilityName(),
                dto.getStartTime(),
                dto.getEndTime()
        );
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BookingEventDto {
        private String  eventType;
        private UUID    bookingId;
        private UUID    userId;
        private UUID    facilityId;
        private String  facilityName;
        private Instant startTime;
        private Instant endTime;
        private Instant occurredAt;
    }
}
