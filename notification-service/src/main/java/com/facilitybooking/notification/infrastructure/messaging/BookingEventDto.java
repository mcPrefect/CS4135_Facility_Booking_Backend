package com.facilitybooking.notification.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingEventDto {
    private String eventType;
    private UUID   bookingId;
    private UUID   userId;
    private UUID   facilityId;
    private String facilityName;
    private Instant startTime;
    private Instant endTime;
    private Instant occurredAt;
}
