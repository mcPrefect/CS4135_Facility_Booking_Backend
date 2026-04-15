package com.facilitybooking.bookingservice;

import com.facilitybooking.bookingservice.domain.BookingStatus;
import com.facilitybooking.bookingservice.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the Booking Service.
 *
 * Uses TestContainers to spin up real PostgreSQL and RabbitMQ instances,
 * verifying the end-to-end path from approval decision consumption through
 * domain aggregate state transition and persistence.
 *
 * Tests the following flows:
 * - BookingApproved event consumed → booking status updated to APPROVED
 * - BookingRejected event consumed → booking status updated to REJECTED
 * - FacilityStatusChanged MAINTENANCE event → pending bookings auto-cancelled
 * - Duplicate approval event → idempotency preserves single state transition
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class BookingApprovalIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("booking_test_db")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.12-management-alpine");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",                postgres::getJdbcUrl);
        registry.add("spring.datasource.username",           postgres::getUsername);
        registry.add("spring.datasource.password",           postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform",        () -> "org.hibernate.dialect.PostgreSQLDialect");

        registry.add("spring.rabbitmq.host",     rabbit::getHost);
        registry.add("spring.rabbitmq.port",     rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");

        // Disable Facility Service circuit breaker calls in tests
        registry.add("facility.service.url", () -> "http://localhost:9999");
    }

    @Autowired private RabbitTemplate     rabbitTemplate;
    @Autowired private BookingRepository  bookingRepository;

    @Value("${rabbitmq.exchanges.booking-events:booking.events}")
    private String bookingEventsExchange;

    private UUID userId;
    private UUID facilityId;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        userId     = UUID.randomUUID();
        facilityId = UUID.randomUUID();
    }

    // ── Helper: create a PENDING booking directly in the DB ──────────────────

    private com.facilitybooking.bookingservice.domain.Booking createPendingBooking(UUID bookingId) {
        var slot = com.facilitybooking.bookingservice.domain.TimeSlot.of(
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200));
        var booking = com.facilitybooking.bookingservice.domain.Booking
                .create(userId, facilityId, "Sports Hall", slot, "Team practice");
        // Use reflection to set the bookingId to our known value for assertion
        return bookingRepository.save(booking);
    }

    // ── Test 1: BookingApproved → status transitions to APPROVED ─────────────

    @Test
    @DisplayName("BookingApproved event consumed → booking status updated to APPROVED")
    void bookingApproved_updatesBookingStatusToApproved() {
        var booking = createPendingBooking(UUID.randomUUID());
        var bookingId = booking.getBookingId();

        Map<String, Object> approvalEvent = Map.of(
                "eventType",  "BookingApproved",
                "bookingId",  bookingId.toString(),
                "userId",     userId.toString(),
                "adminId",    UUID.randomUUID().toString(),
                "reason",     "Approved by admin",
                "occurredAt", Instant.now().toString()
        );

        rabbitTemplate.convertAndSend(bookingEventsExchange,
                "booking.approval.bookinapproved", approvalEvent);

        await().atMost(5, TimeUnit.SECONDS).until(() ->
                bookingRepository.findById(bookingId)
                        .map(b -> b.getStatus() == BookingStatus.APPROVED)
                        .orElse(false));

        var updated = bookingRepository.findById(bookingId).orElseThrow();
        assertEquals(BookingStatus.APPROVED, updated.getStatus(),
                "Booking status should be APPROVED after consuming BookingApproved event");
    }

    // ── Test 2: BookingRejected → status transitions to REJECTED ─────────────

    @Test
    @DisplayName("BookingRejected event consumed → booking status updated to REJECTED")
    void bookingRejected_updatesBookingStatusToRejected() {
        var booking = createPendingBooking(UUID.randomUUID());
        var bookingId = booking.getBookingId();

        Map<String, Object> rejectionEvent = Map.of(
                "eventType",  "BookingRejected",
                "bookingId",  bookingId.toString(),
                "userId",     userId.toString(),
                "adminId",    UUID.randomUUID().toString(),
                "reason",     "Facility unavailable during requested period",
                "occurredAt", Instant.now().toString()
        );

        rabbitTemplate.convertAndSend(bookingEventsExchange,
                "booking.approval.bookingrejected", rejectionEvent);

        await().atMost(5, TimeUnit.SECONDS).until(() ->
                bookingRepository.findById(bookingId)
                        .map(b -> b.getStatus() == BookingStatus.REJECTED)
                        .orElse(false));

        var updated = bookingRepository.findById(bookingId).orElseThrow();
        assertEquals(BookingStatus.REJECTED, updated.getStatus(),
                "Booking status should be REJECTED after consuming BookingRejected event");
    }

    // ── Test 3: FacilityStatusChanged MAINTENANCE → bookings auto-cancelled ──

    @Test
    @DisplayName("FacilityStatusChanged to MAINTENANCE → PENDING bookings auto-cancelled")
    void facilityStatusChanged_maintenance_cancelsPendingBookings() {
        var booking = createPendingBooking(UUID.randomUUID());
        var bookingId = booking.getBookingId();

        Map<String, Object> facilityEvent = Map.of(
                "eventType",  "FacilityStatusChanged",
                "facilityId", facilityId.toString(),
                "name",       "Sports Hall",
                "oldStatus",  "AVAILABLE",
                "newStatus",  "MAINTENANCE",
                "reason",     "Emergency floor resurfacing",
                "occurredAt", Instant.now().toString()
        );

        rabbitTemplate.convertAndSend("facility.events",
                "facility.status.changed", facilityEvent);

        await().atMost(5, TimeUnit.SECONDS).until(() ->
                bookingRepository.findById(bookingId)
                        .map(b -> b.getStatus() == BookingStatus.CANCELLED)
                        .orElse(false));

        var updated = bookingRepository.findById(bookingId).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, updated.getStatus(),
                "PENDING booking should be auto-cancelled when facility enters MAINTENANCE");
    }

    // ── Test 4: Duplicate approval event → idempotency ───────────────────────

    @Test
    @DisplayName("Duplicate BookingApproved event → idempotency preserves APPROVED status, no exception")
    void bookingApproved_duplicate_idempotencyPreservesStatus() throws InterruptedException {
        var booking = createPendingBooking(UUID.randomUUID());
        var bookingId = booking.getBookingId();

        Map<String, Object> approvalEvent = Map.of(
                "eventType",  "BookingApproved",
                "bookingId",  bookingId.toString(),
                "userId",     userId.toString(),
                "adminId",    UUID.randomUUID().toString(),
                "reason",     "Approved",
                "occurredAt", Instant.now().toString()
        );

        // Publish the same event twice — simulates RabbitMQ at-least-once redelivery
        rabbitTemplate.convertAndSend(bookingEventsExchange,
                "booking.approval.bookinapproved", approvalEvent);
        rabbitTemplate.convertAndSend(bookingEventsExchange,
                "booking.approval.bookinapproved", approvalEvent);

        await().atMost(5, TimeUnit.SECONDS).until(() ->
                bookingRepository.findById(bookingId)
                        .map(b -> b.getStatus() == BookingStatus.APPROVED)
                        .orElse(false));

        // Allow second message to process
        TimeUnit.MILLISECONDS.sleep(500);

        var updated = bookingRepository.findById(bookingId).orElseThrow();
        assertEquals(BookingStatus.APPROVED, updated.getStatus(),
                "Status should remain APPROVED after duplicate event — no exception thrown");
    }
}
