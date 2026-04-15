package com.facilitybooking.bookingservice.infrastructure.web;

import com.facilitybooking.bookingservice.application.BookingService;
import com.facilitybooking.bookingservice.domain.Booking;
import com.facilitybooking.bookingservice.domain.BookingStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Booking bounded context.
 * Endpoints follow the API spec defined in Phase 1:
 *   POST   /api/v1/bookings            — create booking (FR-06)
 *   GET    /api/v1/bookings/{id}       — get single booking (FR-03)
 *   GET    /api/v1/bookings            — list user's bookings (FR-03)
 *   DELETE /api/v1/bookings/{id}       — cancel booking (FR-07, FR-08)
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * POST /api/v1/bookings
     * Creates a new booking. Validates availability with Facility Service,
     * checks for conflicts, and publishes BookingCreated event.
     */
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal UserDetails principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        UUID userId = UUID.fromString(principal.getUsername());
        String jwtToken = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7) : null;
        Booking booking = bookingService.createBooking(userId, request, jwtToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.from(booking));
    }

    /**
     * GET /api/v1/bookings/{id}
     * Retrieves a single booking. Only accessible by the owning user or ADMIN (FR-09).
     */
    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> getBooking(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId  = UUID.fromString(principal.getUsername());
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        Booking booking = bookingService.getBooking(bookingId, userId, isAdmin);
        return ResponseEntity.ok(BookingResponse.from(booking));
    }

    /**
     * GET /api/v1/bookings?status=PENDING
     * Returns the booking history for the authenticated user (FR-03).
     * Optionally filter by status.
     */
    @GetMapping
    public ResponseEntity<List<BookingResponse>> getBookingsForUser(
            @RequestParam(required = false) BookingStatus status,
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = UUID.fromString(principal.getUsername());
        List<BookingResponse> responses = bookingService
                .getBookingsForUser(userId, status)
                .stream()
                .map(BookingResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * DELETE /api/v1/bookings/{id}
     * Cancels a booking. Rejected if booking is ACTIVE or COMPLETED (FR-07, FR-08).
     */
    @DeleteMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = UUID.fromString(principal.getUsername());
        Booking booking = bookingService.cancelBooking(bookingId, userId);
        return ResponseEntity.ok(BookingResponse.from(booking));
    }
}
