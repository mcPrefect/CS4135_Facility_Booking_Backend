package com.facilitybooking.bookingservice.infrastructure.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * DTO received from the Facility Service availability check endpoint.
 * Acts as part of the Anti-Corruption Layer between Booking and Facility contexts.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FacilityAvailabilityResponse {
    private boolean available;
    private String  facilityName;
    private String  message;
}
