package com.facilitybooking.bookingservice.infrastructure.web;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class BookingRequest {
    @NotNull private UUID    facilityId;
    @NotNull @Future private Instant startTime;
    @NotNull @Future private Instant endTime;
    private String purpose;
}
