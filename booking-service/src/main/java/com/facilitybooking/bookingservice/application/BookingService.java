package com.facilitybooking.bookingservice.application;

import com.facilitybooking.bookingservice.domain.Booking;
import com.facilitybooking.bookingservice.domain.BookingDomainEvent;
import com.facilitybooking.bookingservice.domain.BookingStatus;
import com.facilitybooking.bookingservice.domain.TimeSlot;
import com.facilitybooking.bookingservice.infrastructure.messaging.BookingEventPublisher;
import com.facilitybooking.bookingservice.infrastructure.persistence.FacilityExistsResponse;
import com.facilitybooking.bookingservice.infrastructure.persistence.FacilityServiceClient;
import com.facilitybooking.bookingservice.infrastructure.web.BookingRequest;
import com.facilitybooking.bookingservice.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository      bookingRepository;
    private final FacilityServiceClient  facilityServiceClient;
    private final BookingEventPublisher  eventPublisher;

    public BookingService(BookingRepository bookingRepository,
                          FacilityServiceClient facilityServiceClient,
                          BookingEventPublisher eventPublisher) {
        this.bookingRepository     = bookingRepository;
        this.facilityServiceClient = facilityServiceClient;
        this.eventPublisher        = eventPublisher;
    }

    @Transactional
    public Booking createBooking(UUID userId, BookingRequest request, String jwtToken) {
        TimeSlot timeSlot = TimeSlot.of(request.getStartTime(), request.getEndTime());

        // Check facility exists and is bookable with Facility Service (circuit breaker in client)
        FacilityExistsResponse facility = facilityServiceClient
                .checkFacilityBookable(request.getFacilityId(), jwtToken);

        if (!facility.isExists()) {
            throw new BookingConflictException("Facility does not exist: " + request.getFacilityId());
        }
        if (!facility.isBookable()) {
            throw new BookingConflictException(
                    facility.getReason() != null ? facility.getReason() : "Facility is not available for booking.");
        }

        // Local conflict check (double safety, FR-05)
        boolean localConflict = bookingRepository.existsConflictingBooking(
                request.getFacilityId(), request.getStartTime(), request.getEndTime());
        if (localConflict) {
            throw new BookingConflictException("A conflicting booking already exists for this facility and time slot.");
        }

        Booking booking = Booking.create(userId, request.getFacilityId(),
                facility.getName(), timeSlot, request.getPurpose());

        bookingRepository.save(booking);
        publishEvents(booking);
        log.info("Booking created: bookingId={} userId={} facilityId={}", booking.getBookingId(), userId, request.getFacilityId());
        return booking;
    }

    @Transactional
    public Booking cancelBooking(UUID bookingId, UUID requestingUserId) {
        Booking booking = findOrThrow(bookingId);

        // Only the owning user may cancel (FR-09 / NFR-08)
        if (!booking.getUserId().equals(requestingUserId)) {
            throw new BookingAccessDeniedException("You may only cancel your own bookings.");
        }

        booking.cancel();
        bookingRepository.save(booking);
        publishEvents(booking);
        log.info("Booking cancelled: bookingId={}", bookingId);
        return booking;
    }

    @Transactional
    public void approveBooking(UUID bookingId) {
        Booking booking = findOrThrow(bookingId);
        booking.approve();
        bookingRepository.save(booking);
        publishEvents(booking);
        log.info("Booking approved: bookingId={}", bookingId);
    }

    @Transactional
    public void rejectBooking(UUID bookingId) {
        Booking booking = findOrThrow(bookingId);
        booking.reject();
        bookingRepository.save(booking);
        publishEvents(booking);
        log.info("Booking rejected: bookingId={}", bookingId);
    }

    @Transactional(readOnly = true)
    public Booking getBooking(UUID bookingId, UUID requestingUserId, boolean isAdmin) {
        Booking booking = findOrThrow(bookingId);
        if (!isAdmin && !booking.getUserId().equals(requestingUserId)) {
            throw new BookingAccessDeniedException("Access denied to booking " + bookingId);
        }
        return booking;
    }

    @Transactional(readOnly = true)
    public List<Booking> getBookingsForUser(UUID userId, BookingStatus status) {
        if (status != null) return bookingRepository.findByUserIdAndStatus(userId, status);
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Booking findOrThrow(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));
    }

    private void publishEvents(Booking booking) {
        List<BookingDomainEvent> events = booking.pullDomainEvents();
        events.forEach(eventPublisher::publish);
    }
}
