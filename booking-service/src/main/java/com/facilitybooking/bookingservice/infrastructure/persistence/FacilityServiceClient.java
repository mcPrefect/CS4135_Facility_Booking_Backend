package com.facilitybooking.bookingservice.infrastructure.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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

    @SuppressWarnings("unchecked")
    public UUID lookupFacilityIdByName(String facilityName, String jwtToken) {
        String encoded = URLEncoder.encode(facilityName, StandardCharsets.UTF_8);
        String url = facilityServiceUrl + "/api/v1/facilities/lookup/batch?names=" + encoded;
        log.info("Looking up facility by name: '{}' url={}", facilityName, url);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (jwtToken != null && !jwtToken.isBlank()) headers.setBearerAuth(jwtToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Object[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, Object[].class);
            Object[] items = response.getBody();
            log.info("Facility lookup response: {} items", items == null ? "null" : items.length);
            if (items == null || items.length == 0) {
                log.warn("No facility found for name: '{}'", facilityName);
                return null;
            }
            Map<String, Object> first = (Map<String, Object>) items[0];
            String facilityIdStr = (String) first.get("facilityId");
            if (facilityIdStr == null) {
                log.warn("Facility found but facilityId missing in response for '{}'", facilityName);
                return null;
            }
            UUID id = UUID.fromString(facilityIdStr);
            log.info("Resolved facility '{}' to id={}", facilityName, id);
            return id;
        } catch (Exception e) {
            log.error("Facility name lookup failed for '{}': {}", facilityName, e.getMessage(), e);
            return null;
        }
    }
}
