package com.facilitybooking.approvalservice;

import com.facilitybooking.approvalservice.domain.ApprovalDecision;
import com.facilitybooking.approvalservice.domain.ApprovalDomainEvent;
import com.facilitybooking.approvalservice.domain.ApprovalStatus;
import com.facilitybooking.approvalservice.domain.ApprovalTask;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalTaskAggregateTest {

    private static final UUID BOOKING_ID = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();
    private static final UUID ADMIN_ID   = UUID.randomUUID();

    private ApprovalTask validTask() {
        return ApprovalTask.create(
                BOOKING_ID, USER_ID, "Sports Hall",
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(2, ChronoUnit.DAYS));
    }

    // ── Creation ──────────────────────────────────────────────────────────────

    @Test
    void create_validArgs_statusIsPending() {
        ApprovalTask t = validTask();
        assertEquals(ApprovalStatus.PENDING, t.getStatus());
        assertNotNull(t.getTaskId());
        assertNotNull(t.getCreatedAt());
        assertNull(t.getDecision());
    }

    @Test
    void create_nullBookingId_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            ApprovalTask.create(null, USER_ID, "Hall",
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200)));
    }

    @Test
    void create_nullUserId_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            ApprovalTask.create(BOOKING_ID, null, "Hall",
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200)));
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    @Test
    void approve_fromPending_statusIsApproved() {
        ApprovalTask t = validTask();
        ApprovalDomainEvent event = t.approve(ADMIN_ID, "Looks good");
        assertEquals(ApprovalStatus.APPROVED, t.getStatus());
        assertNotNull(t.getDecision());
        assertEquals(ApprovalStatus.APPROVED, t.getDecision().getOutcome());
        assertEquals("BookingApproved", event.eventType());
        assertEquals(BOOKING_ID, event.bookingId());
    }

    @Test
    void approve_fromApproved_throwsIllegalState() {
        ApprovalTask t = validTask();
        t.approve(ADMIN_ID, "OK");
        assertThrows(IllegalStateException.class, () -> t.approve(ADMIN_ID, "Again"));
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    @Test
    void reject_fromPending_statusIsRejected() {
        ApprovalTask t = validTask();
        ApprovalDomainEvent event = t.reject(ADMIN_ID, "Facility unavailable");
        assertEquals(ApprovalStatus.REJECTED, t.getStatus());
        assertEquals(ApprovalStatus.REJECTED, t.getDecision().getOutcome());
        assertEquals("Facility unavailable", t.getDecision().getReason());
        assertEquals("BookingRejected", event.eventType());
    }

    @Test
    void reject_fromRejected_throwsIllegalState() {
        ApprovalTask t = validTask();
        t.reject(ADMIN_ID, "No capacity");
        assertThrows(IllegalStateException.class, () -> t.reject(ADMIN_ID, "Again"));
    }

    @Test
    void approve_afterReject_throwsIllegalState() {
        ApprovalTask t = validTask();
        t.reject(ADMIN_ID, "No capacity");
        assertThrows(IllegalStateException.class, () -> t.approve(ADMIN_ID, "Changed mind"));
    }
}

class ApprovalDecisionValueObjectTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Test
    void approve_validArgs_createsDecision() {
        ApprovalDecision d = ApprovalDecision.approve(ADMIN_ID, "All clear");
        assertEquals(ApprovalStatus.APPROVED, d.getOutcome());
        assertEquals(ADMIN_ID, d.getAdminId());
        assertNotNull(d.getDecidedAt());
    }

    @Test
    void approve_nullAdminId_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            ApprovalDecision.approve(null, "reason"));
    }

    @Test
    void reject_validArgs_createsDecision() {
        ApprovalDecision d = ApprovalDecision.reject(ADMIN_ID, "Facility double booked");
        assertEquals(ApprovalStatus.REJECTED, d.getOutcome());
        assertEquals("Facility double booked", d.getReason());
    }

    @Test
    void reject_blankReason_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            ApprovalDecision.reject(ADMIN_ID, ""));
    }

    @Test
    void reject_nullReason_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            ApprovalDecision.reject(ADMIN_ID, null));
    }

    @Test
    void reject_nullAdminId_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            ApprovalDecision.reject(null, "reason"));
    }
}
