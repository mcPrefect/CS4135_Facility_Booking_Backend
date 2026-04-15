package com.facilitybooking.notification;

import com.facilitybooking.notification.domain.NotificationStatus;
import com.facilitybooking.notification.domain.NotificationType;
import com.facilitybooking.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
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


@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class NotificationListenerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("notification_test_db")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.12-management-alpine");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // Override datasource to use TestContainers PostgreSQL
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username",  postgres::getUsername);
        registry.add("spring.datasource.password",  postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");

        // Override RabbitMQ to use TestContainers instance
        registry.add("spring.rabbitmq.host",     rabbit::getHost);
        registry.add("spring.rabbitmq.port",     rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    @Autowired private RabbitTemplate            rabbitTemplate;
    @Autowired private NotificationRepository    notificationRepository;

    // Mock the mail sender — IN_APP channel is used in tests, but bean must be present
    @MockBean  private JavaMailSender javaMailSender;

    @Value("${rabbitmq.exchanges.booking-events:booking.events}")
    private String bookingEventsExchange;

    private UUID recipientId;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        recipientId = UUID.randomUUID();
    }

    @Test
    @DisplayName("BookingApproved event → BOOKING_CONFIRMED notification persisted with SENT status")
    void bookingApproved_createsConfirmedNotification() {
        Map<String, Object> event = Map.of(
                "eventType",    "BookingApproved",
                "bookingId",    UUID.randomUUID().toString(),
                "userId",       recipientId.toString(),
                "facilityId",   UUID.randomUUID().toString(),
                "facilityName", "Computer Lab",
                "startTime",    Instant.parse("2026-04-10T14:00:00Z").toString(),
                "endTime",      Instant.parse("2026-04-10T15:00:00Z").toString(),
                "occurredAt",   Instant.now().toString()
        );

        rabbitTemplate.convertAndSend(bookingEventsExchange, "booking.approved", event);

        // Wait up to 5 seconds for the async listener to process the message
        await().atMost(5, TimeUnit.SECONDS).until(() ->
                notificationRepository.existsByRecipientIdAndTypeAndStatus(
                        recipientId,
                        NotificationType.BOOKING_CONFIRMED,
                        NotificationStatus.SENT));

        var notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
        assertEquals(1, notifications.size(), "Exactly one notification should be created");

        var n = notifications.get(0);
        assertEquals(NotificationType.BOOKING_CONFIRMED, n.getType());
        assertEquals(NotificationStatus.SENT, n.getStatus());
        assertTrue(n.getMessage().contains("Computer Lab"),
                "Message should reference facility name");
        assertNotNull(n.getSentAt(), "sentAt should be set after dispatch");
        assertFalse(n.isRead(), "Notification should start unread");
    }

    @Test
    @DisplayName("BookingCreated event → BOOKING_PENDING_APPROVAL notification persisted")
    void bookingCreated_createsPendingApprovalNotification() {
        Map<String, Object> event = Map.of(
                "eventType",    "BookingCreated",
                "bookingId",    UUID.randomUUID().toString(),
                "userId",       recipientId.toString(),
                "facilityId",   UUID.randomUUID().toString(),
                "facilityName", "Sports Hall",
                "startTime",    Instant.parse("2026-04-11T10:00:00Z").toString(),
                "endTime",      Instant.parse("2026-04-11T11:00:00Z").toString(),
                "occurredAt",   Instant.now().toString()
        );

        rabbitTemplate.convertAndSend(bookingEventsExchange, "booking.created", event);

        await().atMost(5, TimeUnit.SECONDS).until(() ->
                notificationRepository.existsByRecipientIdAndTypeAndStatus(
                        recipientId,
                        NotificationType.BOOKING_PENDING_APPROVAL,
                        NotificationStatus.SENT));

        var notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
        assertEquals(1, notifications.size());
        assertEquals(NotificationType.BOOKING_PENDING_APPROVAL, notifications.get(0).getType());
    }

    @Test
    @DisplayName("BookingRejected event → BOOKING_REJECTED notification persisted")
    void bookingRejected_createsRejectedNotification() {
        Map<String, Object> event = Map.of(
                "eventType",    "BookingRejected",
                "bookingId",    UUID.randomUUID().toString(),
                "userId",       recipientId.toString(),
                "facilityName", "Seminar Room A",
                "startTime",    Instant.parse("2026-04-12T09:00:00Z").toString(),
                "endTime",      Instant.parse("2026-04-12T10:00:00Z").toString(),
                "occurredAt",   Instant.now().toString()
        );

        rabbitTemplate.convertAndSend(bookingEventsExchange, "booking.rejected", event);

        await().atMost(5, TimeUnit.SECONDS).until(() ->
                notificationRepository.existsByRecipientIdAndTypeAndStatus(
                        recipientId,
                        NotificationType.BOOKING_REJECTED,
                        NotificationStatus.SENT));

        assertTrue(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId)
                .stream().anyMatch(n -> n.getType() == NotificationType.BOOKING_REJECTED
                                     && n.getStatus() == NotificationStatus.SENT));
    }

    @Test
    @DisplayName("Idempotency: duplicate BookingApproved event does not create second notification")
    void bookingApproved_duplicate_idempotencyPreventsDouble() throws InterruptedException {
        Map<String, Object> event = Map.of(
                "eventType",    "BookingApproved",
                "bookingId",    UUID.randomUUID().toString(),
                "userId",       recipientId.toString(),
                "facilityName", "Computer Lab 2.01",
                "startTime",    Instant.parse("2026-04-13T14:00:00Z").toString(),
                "endTime",      Instant.parse("2026-04-13T15:00:00Z").toString(),
                "occurredAt",   Instant.now().toString()
        );

        // Publish the same event twice (simulates RabbitMQ at-least-once redelivery)
        rabbitTemplate.convertAndSend(bookingEventsExchange, "booking.approved", event);
        rabbitTemplate.convertAndSend(bookingEventsExchange, "booking.approved", event);

        // Wait for first notification to be processed
        await().atMost(5, TimeUnit.SECONDS).until(() ->
                notificationRepository.existsByRecipientIdAndTypeAndStatus(
                        recipientId,
                        NotificationType.BOOKING_CONFIRMED,
                        NotificationStatus.SENT));

        // Small delay to allow second message to be processed
        TimeUnit.MILLISECONDS.sleep(500);

        long count = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId)
                .stream()
                .filter(n -> n.getType() == NotificationType.BOOKING_CONFIRMED)
                .count();

        assertEquals(1, count, "Idempotency check must prevent duplicate BOOKING_CONFIRMED notifications");
    }

    @Test
    @DisplayName("Malformed event (missing userId) is discarded by ACL – no notification created")
    void malformedEvent_missingUserId_discardedByAcl() throws InterruptedException {
        // Event missing userId — ACL should discard it
        Map<String, Object> malformedEvent = Map.of(
                "eventType",    "BookingApproved",
                "bookingId",    UUID.randomUUID().toString(),
                "facilityName", "Sports Hall",
                "startTime",    Instant.now().toString(),
                "occurredAt",   Instant.now().toString()
        );

        rabbitTemplate.convertAndSend(bookingEventsExchange, "booking.approved", malformedEvent);

        // Allow time for processing
        TimeUnit.MILLISECONDS.sleep(1000);

        assertTrue(notificationRepository.findAll().isEmpty(),
                "Malformed event should be discarded by ACL; no notification should be persisted");
    }

    @Test
    @DisplayName("Unknown event type is discarded by ACL – no notification created")
    void unknownEventType_discardedByAcl() throws InterruptedException {
        Map<String, Object> unknownEvent = Map.of(
                "eventType",  "FacilityMaintenanceScheduled",  // not a booking event
                "bookingId",  UUID.randomUUID().toString(),
                "userId",     recipientId.toString(),
                "occurredAt", Instant.now().toString()
        );

        rabbitTemplate.convertAndSend(bookingEventsExchange, "booking.maintenance", unknownEvent);

        TimeUnit.MILLISECONDS.sleep(1000);

        assertTrue(notificationRepository.findAll().isEmpty(),
                "Unknown event type should be discarded; no notification should be persisted");
    }
}
