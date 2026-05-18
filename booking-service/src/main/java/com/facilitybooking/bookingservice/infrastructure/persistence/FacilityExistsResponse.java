package com.facilitybooking.bookingservice.infrastructure.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * DTO received from the Facility Service /api/v1/facilities/{id}/exists endpoint.
 * Anti-Corruption Layer between Booking and Facility contexts.
 * Schema defined by Eryk Marcinkowski (Facility Service owner).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FacilityExistsResponse {
    private boolean exists;
    private String  facilityId;
    private String  name;
    private String  status;
    private boolean isBookable;
    private String  reason;
}
