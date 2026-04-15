package com.plassey.facilityservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity within the Facility aggregate root.
 * A scheduled period when a facility is unavailable.
 * INV-L5: startTime must be before endTime.
 * INV-F6: Windows for the same facility cannot overlap (enforced by aggregate root).
 */
public class MaintenanceWindow {

    private final UUID id;
    private final Instant startTime;
    private final Instant endTime;
    private final String reason;

    public MaintenanceWindow(UUID id, Instant startTime, Instant endTime, String reason) {
        Objects.requireNonNull(id, "MaintenanceWindow id must not be null");
        Objects.requireNonNull(startTime, "StartTime must not be null");
        Objects.requireNonNull(endTime, "EndTime must not be null");
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Maintenance window startTime must be before endTime");
        }
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reason = reason;
    }

    public boolean overlapsWith(MaintenanceWindow other) {
        return this.startTime.isBefore(other.endTime) && this.endTime.isAfter(other.startTime);
    }

    public boolean isActiveAt(Instant moment) {
        return !moment.isBefore(startTime) && moment.isBefore(endTime);
    }

    // Getters
    public UUID getId() { return id; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public String getReason() { return reason; }
}
