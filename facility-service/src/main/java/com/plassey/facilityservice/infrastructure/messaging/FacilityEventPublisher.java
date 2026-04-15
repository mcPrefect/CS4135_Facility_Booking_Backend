package com.plassey.facilityservice.infrastructure.messaging;

import com.plassey.facilityservice.domain.events.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.util.HashMap;
import java.util.Map;
/**
 * Outbound ACL: publishes domain events to RabbitMQ.
 *
 * Wrapped with Resilience4j @CircuitBreaker and @Retry so RabbitMQ outages
 * don't cascade into the Facility API (NFR-06 resilience pattern).
 *
 * Timestamp normalisation, enum serialisation, and schema versioning
 * are applied here (Outbound ACL contract).
 */
@Profile("!test")       
@Component
public class FacilityEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FacilityEventPublisher.class);

    @Value("${rabbitmq.exchange.facility:facility.events}")
    private String exchange;

    private final RabbitTemplate rabbitTemplate;

    public FacilityEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @CircuitBreaker(name = "facilityPublisher", fallbackMethod = "publishFallback")
    @Retry(name = "facilityPublisher")
    public void publish(FacilityDomainEvent event) {
        String routingKey = routingKeyFor(event);
        Map<String, Object> payload = buildPayload(event);

        log.debug("Publishing event [{}] to exchange [{}] with routing key [{}]",
                event.eventType(), exchange, routingKey);

        rabbitTemplate.convertAndSend(exchange, routingKey, payload);
    }

    /** Fallback: log the lost event for later reconciliation / DLQ replay */
    public void publishFallback(FacilityDomainEvent event, Throwable t) {
        log.error("Circuit open or retries exhausted for event [{}] facilityId=[{}]: {}",
                event.eventType(), event.facilityId(), t.getMessage());
        // In production: persist to an outbox table for guaranteed delivery
    }

    // -----------------------------------------------------------------------
    // Outbound ACL helpers
    // -----------------------------------------------------------------------

    private String routingKeyFor(FacilityDomainEvent event) {
        return switch (event) {
            case FacilityCreatedEvent      ignored -> "facility.created";
            case FacilityUpdatedEvent      ignored -> "facility.updated";
            case FacilityStatusChangedEvent ignored -> "facility.status.changed";
            case MaintenanceScheduledEvent  ignored -> "facility.maintenance.scheduled";
        };
    }

    private Map<String, Object> buildPayload(FacilityDomainEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType",     event.eventType());
        payload.put("schemaVersion", event.schemaVersion());
        payload.put("facilityId",    event.facilityId().toString());
        payload.put("occurredAt",    event.occurredAt().toString()); // ISO-8601

        switch (event) {
            case FacilityCreatedEvent e -> {
                payload.put("name",     e.name());
                payload.put("type",     e.type().name());          // uppercase string
                payload.put("capacity", e.capacity());
                if (e.location() != null) {
                    payload.put("location", Map.of(
                            "building", e.location().building(),
                            "floor",    e.location().floor(),
                            "room",     e.location().room()
                    ));
                }
            }
            case FacilityUpdatedEvent e -> payload.put("changedFields", e.changedFields());
            case FacilityStatusChangedEvent e -> {
                payload.put("name",      e.name());
                payload.put("oldStatus", e.oldStatus().name());    // uppercase string
                payload.put("newStatus", e.newStatus().name());
                if (e.reason() != null) payload.put("reason", e.reason()); // omit nulls (Outbound ACL)
            }
            case MaintenanceScheduledEvent e -> {
                payload.put("maintenanceWindowId", e.maintenanceWindowId().toString());
                payload.put("startTime",           e.startTime().toString());
                payload.put("endTime",             e.endTime().toString());
                if (e.reason() != null) payload.put("reason", e.reason());
            }
        }
        return payload;
    }
}
