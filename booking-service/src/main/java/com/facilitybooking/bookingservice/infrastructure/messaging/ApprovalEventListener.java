package com.facilitybooking.bookingservice.infrastructure.messaging;

import com.facilitybooking.bookingservice.application.BookingService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Listens for approval decisions published by the Approval Service.
 * Translates approval/rejection events back into Booking state transitions.
 */
@Component
public class ApprovalEventListener {

    private static final Logger log = LoggerFactory.getLogger(ApprovalEventListener.class);

    private final BookingService bookingService;

    public ApprovalEventListener(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @RabbitListener(queues = "${rabbitmq.queues.approval-events}")
    public void handleApprovalEvent(ApprovalEventDto dto) {
        log.info("Received approval event: type={} bookingId={}", dto.getEventType(), dto.getBookingId());
        switch (dto.getEventType()) {
            case "BookingApproved" -> bookingService.approveBooking(dto.getBookingId());
            case "BookingRejected" -> bookingService.rejectBooking(dto.getBookingId());
            default -> log.warn("Unknown approval event type: {}", dto.getEventType());
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApprovalEventDto {
        private String eventType;
        private UUID   bookingId;
        private UUID   adminId;
        private String reason;
    }
}
