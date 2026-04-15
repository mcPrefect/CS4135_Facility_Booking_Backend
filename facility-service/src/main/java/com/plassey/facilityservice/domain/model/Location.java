package com.plassey.facilityservice.domain.model;

import java.util.Objects;

/**
 * Immutable value object representing the physical location of a facility.
 */
public record Location(String building, int floor, String room) {

    public Location {
        Objects.requireNonNull(building, "Building must not be null");
        if (building.isBlank()) throw new IllegalArgumentException("Building must not be blank");
        Objects.requireNonNull(room, "Room must not be null");
        if (room.isBlank()) throw new IllegalArgumentException("Room must not be blank");
    }
}
