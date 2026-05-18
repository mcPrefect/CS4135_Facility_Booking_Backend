package com.facilitybooking.approvalservice.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event published to RabbitMQ after an approval decision.
 * Consumed by: Booking Service (to update booking state),
 *              Notification Service (to notify the user).
 */
public record ApprovalDomainEvent(
        String  eventType,
        UUID    bookingId,
        UUID    userId,
        UUID    adminId,
        String  reason,
        String  facilityName,
        Instant bookingStart,
        Instant bookingEnd
) {
    public Instant occurredAt() { return Instant.now(); }
}
