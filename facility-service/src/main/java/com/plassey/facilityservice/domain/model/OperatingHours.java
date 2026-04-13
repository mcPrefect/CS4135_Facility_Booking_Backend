package com.plassey.facilityservice.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Value object encoding weekly operating hours for a facility.
 * INV-F7: startTime must be before endTime.
 */
public record OperatingHours(LocalTime startTime, LocalTime endTime, Set<DayOfWeek> daysOfWeek) {

    public OperatingHours {
        Objects.requireNonNull(startTime, "Start time must not be null");
        Objects.requireNonNull(endTime, "End time must not be null");
        Objects.requireNonNull(daysOfWeek, "Days of week must not be null");
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Operating hours startTime must be before endTime");
        }
        daysOfWeek = Collections.unmodifiableSet(daysOfWeek);
    }

    public boolean isOpenAt(DayOfWeek day, LocalTime time) {
        return daysOfWeek.contains(day)
                && !time.isBefore(startTime)
                && time.isBefore(endTime);
    }
}
