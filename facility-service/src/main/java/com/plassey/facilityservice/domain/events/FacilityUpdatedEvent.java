package com.plassey.facilityservice.domain.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FacilityUpdatedEvent(
        UUID facilityId,
        Map<String, String> changedFields,
        Instant occurredAt
) implements FacilityDomainEvent {
    @Override public String eventType() { return "FacilityUpdated"; }
    @Override public String schemaVersion() { return "1.0"; }
}
