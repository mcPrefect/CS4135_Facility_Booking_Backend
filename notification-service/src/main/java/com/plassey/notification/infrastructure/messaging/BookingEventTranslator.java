package com.plassey.notification.infrastructure.messaging;

import com.plassey.notification.domain.Channel;
import com.plassey.notification.domain.Notification;
import com.plassey.notification.domain.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Anti-Corruption Layer (ACL): translates raw Booking/Approval event DTOs
 * into Notification domain objects, shielding the domain from schema changes
 * in upstream contexts.
 */
@Component
public class BookingEventTranslator {

    private static final Logger log = LoggerFactory.getLogger(BookingEventTranslator.class);
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(ZoneId.of("Europe/Dublin"));

    public Optional<Notification> translate(BookingEventDto dto) {
        if (dto.getUserId() == null || dto.getBookingId() == null) {
            log.warn("ACL: Rejecting malformed event – missing userId or bookingId. eventType={}", dto.getEventType());
            return Optional.empty();
        }

        NotificationType type = mapEventType(dto.getEventType());
        if (type == null) {
            log.warn("ACL: Unknown eventType '{}', discarding.", dto.getEventType());
            return Optional.empty();
        }

        String message = buildMessage(type, dto);
        return Optional.of(Notification.create(dto.getUserId(), type, Channel.IN_APP, message));
    }

    private NotificationType mapEventType(String eventType) {
        if (eventType == null) return null;
        return switch (eventType) {
            case "BookingCreated"   -> NotificationType.BOOKING_PENDING_APPROVAL;
            case "BookingApproved"  -> NotificationType.BOOKING_CONFIRMED;
            case "BookingRejected"  -> NotificationType.BOOKING_REJECTED;
            case "BookingCancelled" -> NotificationType.BOOKING_CANCELLED;
            default -> null;
        };
    }

    private String buildMessage(NotificationType type, BookingEventDto dto) {
        String facility = dto.getFacilityName() != null ? dto.getFacilityName() : "the requested facility";
        String time     = dto.getStartTime() != null ? FMT.format(dto.getStartTime()) : "the requested time";
        return switch (type) {
            case BOOKING_CONFIRMED         -> "Your booking for " + facility + " on " + time + " has been approved.";
            case BOOKING_REJECTED          -> "Your booking for " + facility + " on " + time + " has been rejected.";
            case BOOKING_CANCELLED         -> "Your booking for " + facility + " on " + time + " has been cancelled.";
            case BOOKING_PENDING_APPROVAL  -> "Your booking request for " + facility + " on " + time + " is pending approval.";
        };
    }
}
