package com.plassey.facilityservice.domain.events;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceScheduledEvent(
        UUID facilityId,
        UUID maintenanceWindowId,
        Instant startTime,
        Instant endTime,
        String reason,
        Instant occurredAt
) implements FacilityDomainEvent {
    @Override public String eventType() { return "MaintenanceScheduled"; }
    @Override public String schemaVersion() { return "1.0"; }
}
