package com.facilitybooking.approvalservice.infrastructure.web;

import com.facilitybooking.approvalservice.application.ApprovalService;
import com.facilitybooking.approvalservice.domain.ApprovalTask;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Approval bounded context.
 *
 * Endpoints per A4.6 contract matrix:
 *   PATCH /api/v1/approvals/{bookingId}/approve  — ADMIN only
 *   PATCH /api/v1/approvals/{bookingId}/reject   — ADMIN only
 *   GET   /api/v1/approvals/pending              — ADMIN only
 *   GET   /api/v1/approvals/{bookingId}          — ADMIN only
 */
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * PATCH /api/v1/approvals/{bookingId}/approve
     * Admin approves a pending booking. Publishes BookingApproved event.
     */
    @PatchMapping("/{bookingId}/approve")
    public ResponseEntity<ApprovalResponse> approve(
            @PathVariable UUID bookingId,
            @RequestBody(required = false) ApprovalRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        UUID adminId = UUID.fromString(principal.getUsername());
        String reason = request != null ? request.getReason() : null;
        ApprovalTask task = approvalService.approve(bookingId, adminId, reason);
        return ResponseEntity.ok(ApprovalResponse.from(task));
    }

    /**
     * PATCH /api/v1/approvals/{bookingId}/reject
     * Admin rejects a pending booking. Publishes BookingRejected event.
     */
    @PatchMapping("/{bookingId}/reject")
    public ResponseEntity<ApprovalResponse> reject(
            @PathVariable UUID bookingId,
            @RequestBody ApprovalRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        UUID adminId = UUID.fromString(principal.getUsername());
        ApprovalTask task = approvalService.reject(bookingId, adminId, request.getReason());
        return ResponseEntity.ok(ApprovalResponse.from(task));
    }

    /**
     * GET /api/v1/approvals/pending
     * Returns all pending approval tasks for admin review.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<ApprovalResponse>> getPending() {
        List<ApprovalResponse> tasks = approvalService.getPendingTasks()
                .stream()
                .map(ApprovalResponse::from)
                .toList();
        return ResponseEntity.ok(tasks);
    }

    /**
     * GET /api/v1/approvals/{bookingId}
     * Returns the approval task for a specific booking.
     */
    @GetMapping("/{bookingId}")
    public ResponseEntity<ApprovalResponse> getByBookingId(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(ApprovalResponse.from(approvalService.getByBookingId(bookingId)));
    }
}
