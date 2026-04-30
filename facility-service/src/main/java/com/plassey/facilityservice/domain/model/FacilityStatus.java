package com.plassey.facilityservice.domain.model;

public enum FacilityStatus {
    AVAILABLE,
    OCCUPIED,
    MAINTENANCE,
    RESTRICTED,
    RETIRED;
    
        /**
         * Validates a status transition is legal.
         * INV-F4: RETIRED is terminal – no transitions out.
         * Explicit matrix: AVAILABLE is the only hub state.
         * OCCUPIED/MAINTENANCE/RESTRICTED can only return to AVAILABLE or go RETIRED.
         */
    public boolean canTransitionTo(FacilityStatus target) {
        if (this == target) return false;
        if (this == RETIRED) return false;
        return switch (this) {
            case AVAILABLE   -> target == OCCUPIED
                             || target == MAINTENANCE
                             || target == RESTRICTED
                             || target == RETIRED;
            case OCCUPIED    -> target == AVAILABLE || target == RETIRED;
            case MAINTENANCE -> target == AVAILABLE || target == RETIRED;
            case RESTRICTED  -> target == AVAILABLE || target == RETIRED;
            default          -> false;
        };
}
