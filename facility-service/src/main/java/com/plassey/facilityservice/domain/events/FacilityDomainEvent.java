package com.plassey.facilityservice.domain.events;

import java.time.Instant;
import java.util.UUID;

public sealed interface FacilityDomainEvent
        permits FacilityCreatedEvent, FacilityUpdatedEvent,
                FacilityStatusChangedEvent, MaintenanceScheduledEvent {
    UUID facilityId();
    Instant occurredAt();
    String eventType();
    String schemaVersion();
}
