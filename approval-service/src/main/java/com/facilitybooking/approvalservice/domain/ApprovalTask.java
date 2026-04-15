package com.facilitybooking.approvalservice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate Root: ApprovalTask.
 *
 * Represents a pending administrator review for a booking request.
 * Created when a BookingCreated event is consumed from RabbitMQ.
 *
 * Invariants:
 *   INV-AT1: bookingId must not be null
 *   INV-AT2: userId must not be null
 *   INV-AT3: a task begins in PENDING status
 *   INV-AT4: a task can only be decided once (PENDING → APPROVED or REJECTED)
 *   INV-AT5: decision must not be null when status is APPROVED or REJECTED
 */
@Entity
@Table(name = "approval_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalTask {

    @Id
    private UUID taskId;

    @Column(nullable = false, unique = true)
    private UUID bookingId;

    @Column(nullable = false)
    private UUID userId;

    private String facilityName;

    private Instant bookingStart;
    private Instant bookingEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    @Embedded
    private ApprovalDecision decision;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ApprovalTask create(UUID bookingId, UUID userId,
                                      String facilityName, Instant bookingStart, Instant bookingEnd) {
        if (bookingId == null) throw new IllegalArgumentException("INV-AT1: bookingId must not be null");
        if (userId == null)    throw new IllegalArgumentException("INV-AT2: userId must not be null");

        ApprovalTask t = new ApprovalTask();
        t.taskId       = UUID.randomUUID();
        t.bookingId    = bookingId;
        t.userId       = userId;
        t.facilityName = facilityName;
        t.bookingStart = bookingStart;
        t.bookingEnd   = bookingEnd;
        t.status       = ApprovalStatus.PENDING;   // INV-AT3
        t.createdAt    = Instant.now();
        t.updatedAt    = t.createdAt;
        return t;
    }

    // ── State transitions (INV-AT4) ───────────────────────────────────────────

    public ApprovalDomainEvent approve(UUID adminId, String reason) {
        assertPending("approve");
        this.decision  = ApprovalDecision.approve(adminId, reason);
        this.status    = ApprovalStatus.APPROVED;
        this.updatedAt = Instant.now();
        return new ApprovalDomainEvent("BookingApproved", bookingId, userId, adminId, reason, facilityName, bookingStart, bookingEnd);
    }

    public ApprovalDomainEvent reject(UUID adminId, String reason) {
        assertPending("reject");
        this.decision  = ApprovalDecision.reject(adminId, reason);
        this.status    = ApprovalStatus.REJECTED;
        this.updatedAt = Instant.now();
        return new ApprovalDomainEvent("BookingRejected", bookingId, userId, adminId, reason, facilityName, bookingStart, bookingEnd);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertPending(String operation) {
        if (this.status != ApprovalStatus.PENDING) {
            throw new IllegalStateException(
                "INV-AT4: Cannot call '" + operation + "' on task in state "
                + this.status + " (bookingId=" + bookingId + ")");
        }
    }
}
