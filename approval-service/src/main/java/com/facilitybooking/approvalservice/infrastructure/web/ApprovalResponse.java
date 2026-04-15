package com.facilitybooking.approvalservice.infrastructure.web;

import com.facilitybooking.approvalservice.domain.ApprovalStatus;
import com.facilitybooking.approvalservice.domain.ApprovalTask;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class ApprovalResponse {
    private UUID           taskId;
    private UUID           bookingId;
    private UUID           userId;
    private String         facilityName;
    private Instant        bookingStart;
    private Instant        bookingEnd;
    private ApprovalStatus status;
    private String         decisionReason;
    private UUID           decidedBy;
    private Instant        decidedAt;
    private Instant        createdAt;

    public static ApprovalResponse from(ApprovalTask t) {
        ApprovalResponse r = new ApprovalResponse();
        r.taskId       = t.getTaskId();
        r.bookingId    = t.getBookingId();
        r.userId       = t.getUserId();
        r.facilityName = t.getFacilityName();
        r.bookingStart = t.getBookingStart();
        r.bookingEnd   = t.getBookingEnd();
        r.status       = t.getStatus();
        r.createdAt    = t.getCreatedAt();
        if (t.getDecision() != null) {
            r.decisionReason = t.getDecision().getReason();
            r.decidedBy      = t.getDecision().getAdminId();
            r.decidedAt      = t.getDecision().getDecidedAt();
        }
        return r;
    }
}
