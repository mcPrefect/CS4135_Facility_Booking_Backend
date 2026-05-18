package com.facilitybooking.bookingservice.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event published on all Booking state transitions.
 * Consumed by: Notification Service, Approval Service.
 */
public record BookingDomainEvent(
        String  eventType,
        UUID    bookingId,
        UUID    userId,
        UUID    facilityId,
        String  facilityName,
        Instant startTime,
        Instant endTime
) {
    public Instant occurredAt() { return Instant.now(); }
}
