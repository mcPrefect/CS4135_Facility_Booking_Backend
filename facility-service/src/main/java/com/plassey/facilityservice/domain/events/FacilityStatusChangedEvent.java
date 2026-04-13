package com.plassey.facilityservice.domain.events;

import com.plassey.facilityservice.domain.model.FacilityStatus;

import java.time.Instant;
import java.util.UUID;

public record FacilityStatusChangedEvent(
        UUID facilityId,
        String name,
        FacilityStatus oldStatus,
        FacilityStatus newStatus,
        String reason,
        Instant occurredAt
) implements FacilityDomainEvent {
    @Override public String eventType() { return "FacilityStatusChanged"; }
    @Override public String schemaVersion() { return "1.0"; }
}
