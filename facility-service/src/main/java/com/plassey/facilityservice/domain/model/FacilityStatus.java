package com.plassey.facilityservice.domain.model;

public enum FacilityStatus {
    AVAILABLE,
    OCCUPIED,
    MAINTENANCE,
    RESTRICTED,
    RETIRED;

    /**
     * Validates a status transition is legal.
     * INV-L4: RETIRED is terminal – no transitions out.
     */
    public boolean canTransitionTo(FacilityStatus target) {
        if (this == RETIRED) return false;
        if (this == MAINTENANCE && target == AVAILABLE) return true; // requires admin action, allowed
        return true;
    }
}
