package com.facilitybooking.bookingservice.infrastructure.web;

import com.facilitybooking.bookingservice.domain.Booking;
import com.facilitybooking.bookingservice.domain.BookingStatus;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class BookingResponse {
    private UUID          bookingId;
    private UUID          userId;
    private UUID          facilityId;
    private String        facilityName;
    private Instant       startTime;
    private Instant       endTime;
    private String        purpose;
    private BookingStatus status;
    private Instant       createdAt;
    private long          version;

    public static BookingResponse from(Booking b) {
        BookingResponse r = new BookingResponse();
        r.bookingId    = b.getBookingId();
        r.userId       = b.getUserId();
        r.facilityId   = b.getFacilityId();
        r.facilityName = b.getFacilityName();
        r.startTime    = b.getTimeSlot().getStartTime();
        r.endTime      = b.getTimeSlot().getEndTime();
        r.purpose      = b.getPurpose();
        r.status       = b.getStatus();
        r.createdAt    = b.getCreatedAt();
        r.version      = b.getVersion();
        return r;
    }
}
