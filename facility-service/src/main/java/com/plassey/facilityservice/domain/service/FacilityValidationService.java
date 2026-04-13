package com.plassey.facilityservice.domain.service;

import com.plassey.facilityservice.domain.model.Facility;
import com.plassey.facilityservice.domain.model.FacilityStatus;
import com.plassey.facilityservice.domain.repository.FacilityRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service encapsulating validation logic that spans multiple facilities
 * or requires external repository context.
 */
@Service
public class FacilityValidationService {

    private final FacilityRepository facilityRepository;

    public FacilityValidationService(FacilityRepository facilityRepository) {
        this.facilityRepository = facilityRepository;
    }

    /**
     * Validates that a facility exists and is bookable (not MAINTENANCE or RESTRICTED or RETIRED).
     * Used by Booking Context via REST API.
     */
    public ValidationResult validateBookability(UUID facilityId, Instant requestedStart) {
        Optional<Facility> opt = facilityRepository.findById(facilityId);
        if (opt.isEmpty()) {
            return ValidationResult.notFound(facilityId);
        }
        Facility facility = opt.get();

        if (!facility.isBookable()) {
            String reason = buildNotBookableReason(facility, requestedStart);
            return ValidationResult.notBookable(facilityId, facility.getName(),
                    facility.getStatus(), reason);
        }

        if (requestedStart != null && facility.isUnderMaintenanceAt(requestedStart)) {
            return ValidationResult.notBookable(facilityId, facility.getName(),
                    FacilityStatus.MAINTENANCE, "Facility has a scheduled maintenance window at the requested time");
        }

        return ValidationResult.bookable(facilityId, facility.getName(), facility.getStatus());
    }

    public boolean isNameTaken(String name, UUID excludeId) {
        if (excludeId == null) {
            return facilityRepository.existsByNameIgnoreCase(name);
        }
        return facilityRepository.existsByNameIgnoreCaseAndIdNot(name, excludeId);
    }

    private String buildNotBookableReason(Facility facility, Instant requestedStart) {
        return switch (facility.getStatus()) {
            case MAINTENANCE -> "Facility is currently under maintenance";
            case RESTRICTED  -> "Facility access is restricted";
            case RETIRED     -> "Facility has been retired and is no longer available";
            case OCCUPIED    -> "Facility is currently occupied";
            default          -> "Facility is not available for booking";
        };
    }

    // -----------------------------------------------------------------------
    // Result type
    // -----------------------------------------------------------------------

    public record ValidationResult(
            boolean exists,
            boolean isBookable,
            UUID facilityId,
            String name,
            FacilityStatus status,
            String reason
    ) {
        static ValidationResult bookable(UUID id, String name, FacilityStatus status) {
            return new ValidationResult(true, true, id, name, status, null);
        }

        static ValidationResult notBookable(UUID id, String name, FacilityStatus status, String reason) {
            return new ValidationResult(true, false, id, name, status, reason);
        }

        static ValidationResult notFound(UUID id) {
            return new ValidationResult(false, false, id, null, null, "Facility not found");
        }
    }
}
