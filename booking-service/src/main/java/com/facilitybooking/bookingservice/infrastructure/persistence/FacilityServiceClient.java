package com.facilitybooking.bookingservice.infrastructure.persistence;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Anti-Corruption Layer client for the Facility Service.
 * Calls GET /api/v1/facilities/{facilityId}/exists as specified by
 * Eryk Marcinkowski (Facility Service owner, 22374248).
 *
 * Circuit breaker (NFR-07) protects the Booking Service from Facility Service failures.
 * Falls back to isBookable=false when the circuit is open.
 */
@Component
public class FacilityServiceClient {

    private static final Logger log = LoggerFactory.getLogger(FacilityServiceClient.class);

    private final RestTemplate restTemplate;

    @Value("${facility.service.url:http://plassey-facility:8082}")
    private String facilityServiceUrl;

    public FacilityServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @CircuitBreaker(name = "facilityService", fallbackMethod = "facilityUnavailableFallback")
    public FacilityExistsResponse checkFacilityBookable(UUID facilityId, String jwtToken) {
        String url = facilityServiceUrl + "/api/v1/facilities/" + facilityId + "/exists";
        log.info("Checking facility bookable: facilityId={}", facilityId);

        HttpHeaders headers = new HttpHeaders();
        if (jwtToken != null) headers.setBearerAuth(jwtToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<FacilityExistsResponse> response =
                restTemplate.exchange(url, HttpMethod.GET, entity, FacilityExistsResponse.class);
        return response.getBody();
    }

    FacilityExistsResponse facilityUnavailableFallback(UUID facilityId, String jwtToken, Throwable ex) {
        log.warn("facilityService circuit open – facilityId={}. Reason: {}", facilityId, ex.getMessage());
        FacilityExistsResponse fallback = new FacilityExistsResponse();
        fallback.setExists(false);
        fallback.setBookable(false);
        fallback.setReason("Facility Service is currently unavailable. Please try again shortly.");
        return fallback;
    }
}
