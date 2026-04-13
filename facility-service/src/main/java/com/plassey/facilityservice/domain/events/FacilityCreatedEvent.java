package com.plassey.facilityservice.domain.events;

import com.plassey.facilityservice.domain.model.FacilityStatus;
import com.plassey.facilityservice.domain.model.FacilityType;
import com.plassey.facilityservice.domain.model.Location;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FacilityCreatedEvent(
        UUID facilityId,
        String name,
        FacilityType type,
        int capacity,
        Location location,
        Instant occurredAt
) implements FacilityDomainEvent {
    @Override public String eventType() { return "FacilityCreated"; }
    @Override public String schemaVersion() { return "1.0"; }
}
