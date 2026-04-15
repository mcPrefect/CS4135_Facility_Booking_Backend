package com.facilitybooking.notification;

import com.facilitybooking.notification.domain.Channel;
import com.facilitybooking.notification.domain.Notification;
import com.facilitybooking.notification.domain.NotificationType;
import com.facilitybooking.notification.repository.LogEntryRepository;
import com.facilitybooking.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationApiIntegrationTest {

    // Fixed UUID used as the "owner" userId throughout tests.
    // Must be a constant so @WithMockUser annotation (owner test) can reference it.
    static final String OWNER_UUID = "11111111-1111-1111-1111-111111111111";

    @Autowired private MockMvc               mockMvc;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private LogEntryRepository     logEntryRepository;

    // Prevents actual SMTP connection attempts during tests
    @MockBean  private JavaMailSender javaMailSender;

    private UUID              ownerId;
    private Notification      savedNotification;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        logEntryRepository.deleteAll();

        ownerId = UUID.fromString(OWNER_UUID);
        Notification n = Notification.create(
                ownerId,
                NotificationType.BOOKING_CONFIRMED,
                Channel.IN_APP,
                "Your booking for Sports Hall on 10 Apr 15:00 has been approved."
        );
        n.markSent();
        savedNotification = notificationRepository.save(n);
    }

    // ── Notification retrieval ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /notifications/{userId} – own user receives their notifications")
    @WithMockUser(username = OWNER_UUID, roles = "USER")
    void getNotifications_ownUser_returnsNotifications() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/{userId}", ownerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type",    is("BOOKING_CONFIRMED")))
                .andExpect(jsonPath("$[0].status",  is("SENT")))
                .andExpect(jsonPath("$[0].message", containsString("Sports Hall")));
    }

    @Test
    @DisplayName("GET /notifications/{userId} – ADMIN can access any user's notifications")
    @WithMockUser(username = "admin-id", roles = "ADMIN")
    void getNotifications_adminRole_canAccessAnyUser() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/{userId}", ownerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("GET /notifications/{userId} – different non-ADMIN user returns 403")
    @WithMockUser(username = "99999999-9999-9999-9999-999999999999", roles = "USER")
    void getNotifications_differentUser_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/{userId}", ownerId))
                .andExpect(status().isForbidden());
    }

    // ── Mark read ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /notifications/{id}/read – marks notification as read; GET reflects change")
    @WithMockUser(username = "admin-id", roles = "ADMIN")
    void markRead_updatesIsRead_getReflectsChange() throws Exception {
        // Confirm initially unread
        mockMvc.perform(get("/api/v1/notifications/{userId}", ownerId))
                .andExpect(jsonPath("$[0].read", is(false)));

        // Mark as read
        mockMvc.perform(patch("/api/v1/notifications/{id}/read",
                        savedNotification.getNotificationId()))
                .andExpect(status().isNoContent());

        // Verify now read
        mockMvc.perform(get("/api/v1/notifications/{userId}", ownerId))
                .andExpect(jsonPath("$[0].read", is(true)));
    }

    // ── Unread count ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /notifications/{userId}/unread-count – decrements after marking read")
    @WithMockUser(username = OWNER_UUID, roles = "USER")
    void unreadCount_decrementsAfterMarkRead() throws Exception {
        // One unread notification created in setUp
        mockMvc.perform(get("/api/v1/notifications/{userId}/unread-count", ownerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", is(1)));

        // Mark read via ADMIN call (to avoid re-testing the owner auth)
        notificationRepository.findById(savedNotification.getNotificationId())
                .ifPresent(n -> { n.markRead(); notificationRepository.save(n); });

        mockMvc.perform(get("/api/v1/notifications/{userId}/unread-count", ownerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", is(0)));
    }

    // ── Log ingestion ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /logs – valid payload persists log entry and returns it")
    @WithMockUser(username = "booking-service", roles = "USER")
    void ingestLog_validPayload_persistsAndReturns() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        String body = """
                {
                    "serviceName":   "booking-service",
                    "level":         "INFO",
                    "message":       "Booking created successfully for facilityId=fac-abc",
                    "correlationId": "%s"
                }
                """.formatted(correlationId);

        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName",   is("booking-service")))
                .andExpect(jsonPath("$.level",         is("INFO")))
                .andExpect(jsonPath("$.correlationId", is(correlationId)))
                .andExpect(jsonPath("$.logEntryId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /logs – invalid log level returns 400 with error message")
    @WithMockUser(username = "booking-service", roles = "USER")
    void ingestLog_invalidLevel_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceName": "booking-service",
                                  "level": "VERBOSE", "message": "test" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Invalid log level")));
    }

    @Test
    @DisplayName("POST /logs – missing serviceName or message returns 400")
    @WithMockUser(username = "nlp-service", roles = "USER")
    void ingestLog_missingRequiredFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"level\": \"INFO\" }"))
                .andExpect(status().isBadRequest());
    }

    // ── Log query (ADMIN only) ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /logs – non-ADMIN user returns 403")
    @WithMockUser(username = "student-user", roles = "USER")
    void getLogs_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /logs – ADMIN returns 200 with paginated results, filtered by service and level")
    @WithMockUser(username = "admin-id", roles = "ADMIN")
    void getLogs_adminWithFilters_returnsPaginatedResults() throws Exception {
        // Seed two log entries
        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceName": "nlp-service", "level": "WARN",
                                  "message": "Low confidence score: 0.42" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceName": "booking-service", "level": "ERROR",
                                  "message": "Facility not found during booking" }
                                """))
                .andExpect(status().isOk());

        // Filter by serviceName=nlp-service, level=WARN → should return 1 result
        mockMvc.perform(get("/api/v1/logs")
                        .param("serviceName", "nlp-service")
                        .param("level", "WARN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content",               hasSize(1)))
                .andExpect(jsonPath("$.content[0].serviceName", is("nlp-service")))
                .andExpect(jsonPath("$.content[0].level",       is("WARN")))
                .andExpect(jsonPath("$.totalElements",           is(1)));
    }
}
