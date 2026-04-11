package com.facilitybooking.bookingservice.infrastructure.persistence;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * Anti-Corruption Layer client for the Facility Service.
 * Circuit breaker (NFR-07) protects the Booking Service from Facility Service failures.
 * Falls back to assuming availability=false, returning HTTP 503 upstream.
 */
@Component
public class FacilityServiceClient {

    private static final Logger log = LoggerFactory.getLogger(FacilityServiceClient.class);

    private final RestTemplate restTemplate;

    @Value("${facility.service.url:http://facility-service}")
    private String facilityServiceUrl;

    public FacilityServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @CircuitBreaker(name = "facilityService", fallbackMethod = "facilityUnavailableFallback")
    public FacilityAvailabilityResponse checkAvailability(UUID facilityId, Instant startTime, Instant endTime) {
        String url = facilityServiceUrl + "/api/v1/facilities/" + facilityId + "/availability"
                + "?start=" + startTime + "&end=" + endTime;
        log.info("Checking availability: facilityId={} start={} end={}", facilityId, startTime, endTime);
        return restTemplate.getForObject(url, FacilityAvailabilityResponse.class);
    }

    FacilityAvailabilityResponse facilityUnavailableFallback(UUID facilityId, Instant startTime,
                                                              Instant endTime, Throwable ex) {
        log.warn("facilityService circuit open – cannot verify availability for facilityId={}. Reason: {}",
                facilityId, ex.getMessage());
        FacilityAvailabilityResponse fallback = new FacilityAvailabilityResponse();
        fallback.setAvailable(false);
        fallback.setMessage("Facility Service is currently unavailable. Please try again shortly.");
        return fallback;
    }
}
