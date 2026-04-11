package com.facilitybooking.bookingservice;

import com.facilitybooking.bookingservice.domain.Booking;
import com.facilitybooking.bookingservice.domain.BookingDomainEvent;
import com.facilitybooking.bookingservice.domain.BookingStatus;
import com.facilitybooking.bookingservice.domain.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BookingAggregateTest {

    private static final UUID USER_ID     = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();

    private Booking validBooking() {
        TimeSlot slot = TimeSlot.of(
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(2, ChronoUnit.DAYS));
        return Booking.create(USER_ID, FACILITY_ID, "Sports Hall", slot, "Team practice");
    }

    // ── Booking creation ──────────────────────────────────────────────────────

    @Test
    void create_validArgs_statusIsPending() {
        Booking b = validBooking();
        assertEquals(BookingStatus.PENDING, b.getStatus());
        assertNotNull(b.getBookingId());
        assertNotNull(b.getCreatedAt());
    }

    @Test
    void create_nullUserId_throws() {
        TimeSlot slot = TimeSlot.of(Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200));
        assertThrows(IllegalArgumentException.class, () ->
                Booking.create(null, FACILITY_ID, "Hall", slot, "purpose"));
    }

    @Test
    void create_nullFacilityId_throws() {
        TimeSlot slot = TimeSlot.of(Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200));
        assertThrows(IllegalArgumentException.class, () ->
                Booking.create(USER_ID, null, "Hall", slot, "purpose"));
    }

    @Test
    void create_publishesBookingCreatedEvent() {
        Booking b = validBooking();
        List<BookingDomainEvent> events = b.pullDomainEvents();
        assertEquals(1, events.size());
        assertEquals("BookingCreated", events.get(0).eventType());
    }

    @Test
    void pullDomainEvents_clearsEventList() {
        Booking b = validBooking();
        b.pullDomainEvents(); // first pull
        assertTrue(b.pullDomainEvents().isEmpty()); // second pull should be empty
    }

    // ── State machine ─────────────────────────────────────────────────────────

    @Test
    void approve_fromPending_statusIsApproved() {
        Booking b = validBooking();
        b.pullDomainEvents(); // clear creation event
        b.approve();
        assertEquals(BookingStatus.APPROVED, b.getStatus());
        assertEquals("BookingApproved", b.pullDomainEvents().get(0).eventType());
    }

    @Test
    void reject_fromPending_statusIsRejected() {
        Booking b = validBooking();
        b.pullDomainEvents();
        b.reject();
        assertEquals(BookingStatus.REJECTED, b.getStatus());
    }

    @Test
    void approve_fromApproved_throwsIllegalState() {
        Booking b = validBooking();
        b.approve();
        assertThrows(IllegalStateException.class, b::approve);
    }

    @Test
    void complete_fromApprovedThenActive_statusIsCompleted() {
        Booking b = validBooking();
        b.approve();
        b.activate();
        b.complete();
        assertEquals(BookingStatus.COMPLETED, b.getStatus());
    }

    // ── Cancellation invariants (INV-B6) ──────────────────────────────────────

    @Test
    void cancel_fromPending_succeeds() {
        Booking b = validBooking();
        b.cancel();
        assertEquals(BookingStatus.CANCELLED, b.getStatus());
    }

    @Test
    void cancel_fromActive_throwsIllegalState() {
        Booking b = validBooking();
        b.approve();
        b.activate();
        assertThrows(IllegalStateException.class, b::cancel);
    }

    @Test
    void cancel_fromCompleted_throwsIllegalState() {
        Booking b = validBooking();
        b.approve();
        b.activate();
        b.complete();
        assertThrows(IllegalStateException.class, b::cancel);
    }

    @Test
    void cancel_publishesBookingCancelledEvent() {
        Booking b = validBooking();
        b.pullDomainEvents();
        b.cancel();
        List<BookingDomainEvent> events = b.pullDomainEvents();
        assertEquals(1, events.size());
        assertEquals("BookingCancelled", events.get(0).eventType());
    }
}

class TimeSlotTest {

    @Test
    void of_validArgs_succeeds() {
        TimeSlot ts = TimeSlot.of(
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200));
        assertNotNull(ts);
    }

    @Test
    void of_nullStart_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                TimeSlot.of(null, Instant.now().plusSeconds(7200)));
    }

    @Test
    void of_nullEnd_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                TimeSlot.of(Instant.now().plusSeconds(3600), null));
    }

    @Test
    void of_startAfterEnd_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                TimeSlot.of(Instant.now().plusSeconds(7200), Instant.now().plusSeconds(3600)));
    }

    @Test
    void of_startInPast_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                TimeSlot.of(Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600)));
    }

    @Test
    void overlapsWith_overlappingSlots_returnsTrue() {
        TimeSlot a = TimeSlot.of(Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200));
        TimeSlot b = TimeSlot.of(Instant.now().plusSeconds(5400), Instant.now().plusSeconds(9000));
        assertTrue(a.overlapsWith(b));
    }

    @Test
    void overlapsWith_nonOverlappingSlots_returnsFalse() {
        TimeSlot a = TimeSlot.of(Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200));
        TimeSlot b = TimeSlot.of(Instant.now().plusSeconds(7200), Instant.now().plusSeconds(10800));
        assertFalse(a.overlapsWith(b));
    }
}
