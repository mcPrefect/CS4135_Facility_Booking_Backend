package com.facilitybooking.bookingservice.repository;

import com.facilitybooking.bookingservice.domain.Booking;
import com.facilitybooking.bookingservice.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findByFacilityIdAndStatusIn(UUID facilityId, List<BookingStatus> statuses);

    List<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Booking> findByUserIdAndStatus(UUID userId, BookingStatus status);

    /**
     * Conflict detection query: checks for any existing non-cancelled/rejected booking
     * for the same facility that overlaps with the requested time slot.
     * Used by ConflictDetectionService to enforce FR-05.
     */
    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b
        WHERE b.facilityId = :facilityId
          AND b.status NOT IN ('CANCELLED', 'REJECTED', 'COMPLETED')
          AND b.timeSlot.startTime < :endTime
          AND b.timeSlot.endTime   > :startTime
    """)
    boolean existsConflictingBooking(
            @Param("facilityId") UUID facilityId,
            @Param("startTime")  Instant startTime,
            @Param("endTime")    Instant endTime
    );
}
