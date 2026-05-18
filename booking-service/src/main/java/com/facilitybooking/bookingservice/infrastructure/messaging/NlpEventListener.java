package com.facilitybooking.bookingservice.infrastructure.messaging;

import com.facilitybooking.bookingservice.application.BookingService;
import com.facilitybooking.bookingservice.infrastructure.persistence.FacilityServiceClient;
import com.facilitybooking.bookingservice.infrastructure.web.BookingRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Component
public class NlpEventListener {

    private static final Logger log = LoggerFactory.getLogger(NlpEventListener.class);

    private final BookingService bookingService;
    private final FacilityServiceClient facilityServiceClient;

    public NlpEventListener(BookingService bookingService, FacilityServiceClient facilityServiceClient) {
        this.bookingService = bookingService;
        this.facilityServiceClient = facilityServiceClient;
    }

    @RabbitListener(queues = "booking.nlp.intent.queue")
    public void handleNlpBookingIntent(NlpBookingIntentDto dto) {
        log.info("Received NLP event: intent={} userId={}", dto.getIntent(), dto.getUserId());

        if (!"CREATE_BOOKING".equals(dto.getIntent())) {
            log.info("Ignoring NLP event with intent: {}", dto.getIntent());
            return;
        }

        Map<String, String> entities = dto.getEntities();
        if (entities == null) {
            log.warn("NLP event has no entities, discarding");
            return;
        }

        String facilityName = entities.get("facility");
        String date         = entities.get("date");      // YYYY-MM-DD
        String time         = entities.get("time");      // HH:MM
        String durationStr  = entities.get("duration");  // minutes

        if (facilityName == null || date == null || time == null) {
            log.warn("NLP event missing required entities (facility/date/time): {}", entities);
            return;
        }

        // Resolve facility name → UUID
        UUID facilityId = facilityServiceClient.lookupFacilityIdByName(facilityName, dto.getJwtToken());
        if (facilityId == null) {
            log.error("Could not resolve facility '{}' to a UUID — booking aborted", facilityName);
            return;
        }

        // Parse date + time → Instant, add duration for endTime
        int durationMinutes = 60;
        try {
            if (durationStr != null && !durationStr.isBlank()) {
                durationMinutes = Integer.parseInt(durationStr.trim());
            }
        } catch (NumberFormatException e) {
            log.warn("Could not parse duration '{}', defaulting to 60 minutes", durationStr);
        }

        Instant startTime;
        try {
            startTime = LocalDateTime.parse(date + "T" + time).toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            log.error("Could not parse date/time '{} {}': {}", date, time, e.getMessage());
            return;
        }
        Instant endTime = startTime.plusSeconds(durationMinutes * 60L);

        // Build and submit booking
        BookingRequest request = new BookingRequest();
        request.setFacilityId(facilityId);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setPurpose("NLP Booking");

        try {
            UUID userId = UUID.fromString(dto.getUserId());
            var booking = bookingService.createBooking(userId, request, dto.getJwtToken());
            log.info("NLP booking created: bookingId={} userId={} facility='{}' start={}",
                    booking.getBookingId(), userId, facilityName, startTime);
        } catch (Exception e) {
            log.error("Failed to create NLP booking for userId={} facility='{}': {}",
                    dto.getUserId(), facilityName, e.getMessage(), e);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NlpBookingIntentDto {
        private String eventType;
        private String queryId;
        private String userId;
        private String intent;
        private double confidence;
        private Map<String, String> entities;
        private String jwtToken;
        private String occurredAt;
    }
}
