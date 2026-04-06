package com.plassey.notification;

import com.plassey.notification.domain.*;
import com.plassey.notification.infrastructure.messaging.BookingEventDto;
import com.plassey.notification.infrastructure.messaging.BookingEventTranslator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationAggregateTest {

    @Test
    void createNotification_validArgs_succeeds() {
        Notification n = Notification.create(UUID.randomUUID(), NotificationType.BOOKING_CONFIRMED, Channel.IN_APP, "Test message");
        assertNotNull(n.getNotificationId());
        assertEquals(NotificationStatus.PENDING, n.getStatus());
        assertFalse(n.isRead());
    }

    @Test
    void createNotification_nullRecipient_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            Notification.create(null, NotificationType.BOOKING_CONFIRMED, Channel.IN_APP, "msg"));
    }

    @Test
    void createNotification_blankMessage_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            Notification.create(UUID.randomUUID(), NotificationType.BOOKING_CONFIRMED, Channel.IN_APP, "  "));
    }

    @Test
    void markSent_fromPending_updatesStatus() {
        Notification n = Notification.create(UUID.randomUUID(), NotificationType.BOOKING_CONFIRMED, Channel.IN_APP, "msg");
        n.markSent();
        assertEquals(NotificationStatus.SENT, n.getStatus());
        assertNotNull(n.getSentAt());
    }

    @Test
    void markSent_fromSent_throwsIllegalState() {
        Notification n = Notification.create(UUID.randomUUID(), NotificationType.BOOKING_CONFIRMED, Channel.IN_APP, "msg");
        n.markSent();
        assertThrows(IllegalStateException.class, n::markSent);
    }

    @Test
    void markFailed_fromPending_updatesStatus() {
        Notification n = Notification.create(UUID.randomUUID(), NotificationType.BOOKING_REJECTED, Channel.EMAIL, "msg");
        n.markFailed("SMTP error");
        assertEquals(NotificationStatus.FAILED, n.getStatus());
        assertEquals("SMTP error", n.getFailureReason());
    }
}

class BookingEventTranslatorTest {

    private final BookingEventTranslator translator = new BookingEventTranslator();

    @Test
    void translate_bookingApproved_returnsConfirmedNotification() {
        BookingEventDto dto = new BookingEventDto();
        dto.setEventType("BookingApproved");
        dto.setUserId(UUID.randomUUID());
        dto.setBookingId(UUID.randomUUID());
        dto.setFacilityName("Sports Hall");
        dto.setStartTime(Instant.now());

        Optional<Notification> result = translator.translate(dto);
        assertTrue(result.isPresent());
        assertEquals(NotificationType.BOOKING_CONFIRMED, result.get().getType());
    }

    @Test
    void translate_missingUserId_returnsEmpty() {
        BookingEventDto dto = new BookingEventDto();
        dto.setEventType("BookingApproved");
        dto.setBookingId(UUID.randomUUID());

        assertTrue(translator.translate(dto).isEmpty());
    }

    @Test
    void translate_unknownEventType_returnsEmpty() {
        BookingEventDto dto = new BookingEventDto();
        dto.setEventType("SomeOtherEvent");
        dto.setUserId(UUID.randomUUID());
        dto.setBookingId(UUID.randomUUID());

        assertTrue(translator.translate(dto).isEmpty());
    }

    @Test
    void logEntry_invariants_enforced() {
        assertThrows(IllegalArgumentException.class, () -> LogEntry.create(null, LogLevel.INFO, "msg", null));
        assertThrows(IllegalArgumentException.class, () -> LogEntry.create("svc", null, "msg", null));
        assertThrows(IllegalArgumentException.class, () -> LogEntry.create("svc", LogLevel.INFO, "", null));
    }
}
