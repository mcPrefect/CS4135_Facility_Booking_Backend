package com.facilitybooking.bookingservice.infrastructure.messaging;

import com.facilitybooking.bookingservice.repository.BookingRepository;
import com.facilitybooking.bookingservice.domain.Booking;
import com.facilitybooking.bookingservice.domain.BookingStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Consumes facility status change events from Eryk's Facility Service.
 * When a facility goes to MAINTENANCE or RETIRED, all pending and approved
 * bookings for that facility are cancelled.
 *
 * Exchange: facility.events
 * Routing key: facility.status.changed
 */
@Component
public class FacilityEventListener {

    private static final Logger log = LoggerFactory.getLogger(FacilityEventListener.class);

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;

    public FacilityEventListener(BookingRepository bookingRepository,
                                  BookingEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.eventPublisher    = eventPublisher;
    }

    @RabbitListener(queues = "booking.facility.status.queue")
    @Transactional
    public void handleFacilityStatusChanged(FacilityStatusChangedDto dto) {
        log.info("Received facility status change: facilityId={} newStatus={}",
                dto.getFacilityId(), dto.getNewStatus());

        if (!"MAINTENANCE".equals(dto.getNewStatus()) && !"RETIRED".equals(dto.getNewStatus())) {
            log.debug("Ignoring facility status change to {} — no action required.", dto.getNewStatus());
            return;
        }

        // Cancel all PENDING and APPROVED bookings for this facility
        List<Booking> affected = bookingRepository
                .findByFacilityIdAndStatusIn(dto.getFacilityId(),
                        List.of(BookingStatus.PENDING, BookingStatus.APPROVED));

        for (Booking booking : affected) {
            try {
                booking.cancel();
                bookingRepository.save(booking);
                booking.pullDomainEvents().forEach(eventPublisher::publish);
                log.info("Auto-cancelled bookingId={} due to facility status change to {}",
                        booking.getBookingId(), dto.getNewStatus());
            } catch (IllegalStateException e) {
                log.warn("Could not cancel bookingId={}: {}", booking.getBookingId(), e.getMessage());
            }
        }

        log.info("Cancelled {} bookings for facilityId={} due to status change to {}",
                affected.size(), dto.getFacilityId(), dto.getNewStatus());
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FacilityStatusChangedDto {
        private String  eventType;
        private String  schemaVersion;
        private UUID    facilityId;
        private String  name;
        private String  oldStatus;
        private String  newStatus;
        private String  reason;
    }
}
