package com.facilitybooking.approvalservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Value Object: ApprovalDecision.
 * Represents the immutable outcome of an administrator's review.
 *
 * Invariants:
 *   INV-AD1: outcome must not be null
 *   INV-AD2: adminId must not be null
 *   INV-AD3: decidedAt must not be null
 *   INV-AD4: reason must not be blank when outcome is REJECTED
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalDecision {

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_outcome")
    private ApprovalStatus outcome;

    @Column(name = "decision_reason")
    private String reason;

    @Column(name = "decided_by")
    private UUID adminId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    public static ApprovalDecision approve(UUID adminId, String reason) {
        if (adminId == null) throw new IllegalArgumentException("INV-AD2: adminId must not be null");
        ApprovalDecision d = new ApprovalDecision();
        d.outcome   = ApprovalStatus.APPROVED;
        d.adminId   = adminId;
        d.reason    = reason;
        d.decidedAt = Instant.now();
        return d;
    }

    public static ApprovalDecision reject(UUID adminId, String reason) {
        if (adminId == null) throw new IllegalArgumentException("INV-AD2: adminId must not be null");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("INV-AD4: reason must not be blank for rejection");
        ApprovalDecision d = new ApprovalDecision();
        d.outcome   = ApprovalStatus.REJECTED;
        d.adminId   = adminId;
        d.reason    = reason;
        d.decidedAt = Instant.now();
        return d;
    }
}
