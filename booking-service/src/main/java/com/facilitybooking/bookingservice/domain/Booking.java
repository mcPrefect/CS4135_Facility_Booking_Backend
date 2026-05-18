package com.facilitybooking.bookingservice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root: Booking.
 *
 * Invariants:
 *   INV-B1: userId must not be null
 *   INV-B2: facilityId must not be null
 *   INV-B3: timeSlot must not be null
 *   INV-B4: a booking begins in PENDING status
 *   INV-B5: state transitions must follow the allowed state machine
 *   INV-B6: cancellation is not permitted once a booking is ACTIVE or COMPLETED
 *   INV-B7: version field is managed by optimistic locking
 */
@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

    @Id
    private UUID bookingId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID facilityId;

    private String facilityName;

    @Embedded
    private TimeSlot timeSlot;

    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    /**
     * Optimistic locking version field (INV-B7).
     * Prevents lost updates under concurrent modification.
     */
    @Version
    private long version;

    @Transient
    private final List<BookingDomainEvent> domainEvents = new ArrayList<>();

    // ── Factory ──────────────────────────────────────────────────────────────

    public static Booking create(UUID userId, UUID facilityId, String facilityName,
                                 TimeSlot timeSlot, String purpose) {
        if (userId == null)     throw new IllegalArgumentException("INV-B1: userId must not be null");
        if (facilityId == null) throw new IllegalArgumentException("INV-B2: facilityId must not be null");
        if (timeSlot == null)   throw new IllegalArgumentException("INV-B3: timeSlot must not be null");

        Booking b = new Booking();
        b.bookingId    = UUID.randomUUID();
        b.userId       = userId;
        b.facilityId   = facilityId;
        b.facilityName = facilityName;
        b.timeSlot     = timeSlot;
        b.purpose      = purpose;
        b.status       = BookingStatus.PENDING;   // INV-B4
        b.createdAt    = Instant.now();
        b.updatedAt    = b.createdAt;

        b.domainEvents.add(new BookingDomainEvent("BookingCreated", b.bookingId, b.userId,
                b.facilityId, b.facilityName, b.timeSlot.getStartTime(), b.timeSlot.getEndTime()));
        return b;
    }

    // ── State machine transitions (INV-B5) ───────────────────────────────────

    public void approve() {
        assertStatus(BookingStatus.PENDING, "approve");
        this.status    = BookingStatus.APPROVED;
        this.updatedAt = Instant.now();
        domainEvents.add(new BookingDomainEvent("BookingApproved", bookingId, userId,
                facilityId, facilityName, timeSlot.getStartTime(), timeSlot.getEndTime()));
    }

    public void reject() {
        assertStatus(BookingStatus.PENDING, "reject");
        this.status    = BookingStatus.REJECTED;
        this.updatedAt = Instant.now();
        domainEvents.add(new BookingDomainEvent("BookingRejected", bookingId, userId,
                facilityId, facilityName, timeSlot.getStartTime(), timeSlot.getEndTime()));
    }

    public void activate() {
        assertStatus(BookingStatus.APPROVED, "activate");
        this.status    = BookingStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        assertStatus(BookingStatus.ACTIVE, "complete");
        this.status    = BookingStatus.COMPLETED;
        this.updatedAt = Instant.now();
        domainEvents.add(new BookingDomainEvent("BookingCompleted", bookingId, userId,
                facilityId, facilityName, timeSlot.getStartTime(), timeSlot.getEndTime()));
    }

    /**
     * Cancellation is not permitted once ACTIVE or COMPLETED (INV-B6).
     */
    public void cancel() {
        if (this.status == BookingStatus.ACTIVE || this.status == BookingStatus.COMPLETED) {
            throw new IllegalStateException(
                    "INV-B6: Cannot cancel booking in state " + this.status + " (bookingId=" + bookingId + ")");
        }
        if (this.status == BookingStatus.CANCELLED) {
            throw new IllegalStateException("INV-B5: Booking is already CANCELLED");
        }
        this.status    = BookingStatus.CANCELLED;
        this.updatedAt = Instant.now();
        domainEvents.add(new BookingDomainEvent("BookingCancelled", bookingId, userId,
                facilityId, facilityName, timeSlot.getStartTime(), timeSlot.getEndTime()));
    }

    // ── Domain events ─────────────────────────────────────────────────────────

    public List<BookingDomainEvent> pullDomainEvents() {
        List<BookingDomainEvent> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return Collections.unmodifiableList(events);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertStatus(BookingStatus required, String operation) {
        if (this.status != required) {
            throw new IllegalStateException(
                    "INV-B5: Cannot call '" + operation + "' on booking in state "
                    + this.status + " (bookingId=" + bookingId + ")");
        }
    }
}
