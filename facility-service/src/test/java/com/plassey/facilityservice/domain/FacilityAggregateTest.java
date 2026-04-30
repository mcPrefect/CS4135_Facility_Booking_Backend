package com.plassey.facilityservice.domain;

import com.plassey.facilityservice.domain.model.*;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class FacilityAggregateTest {

    private static final OperatingHours STANDARD_HOURS = new OperatingHours(
            LocalTime.of(9, 0), LocalTime.of(21, 0),
            Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                   DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));

    private static final Location CS_BUILDING = new Location("CS Building", 2, "2.01");

    // ------------------------------------------------------------------
    // INV-F1: name must be non-blank
    // ------------------------------------------------------------------
    @Test
    void createFacility_withBlankName_throwsException() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                Facility.create("  ", FacilityType.COMPUTER_LAB, 30,
                        CS_BUILDING, STANDARD_HOURS, Set.of()))
                .withMessageContaining("INV-F1");
    }

    // ------------------------------------------------------------------
    // INV-F3: capacity > 0
    // ------------------------------------------------------------------
    @Test
    void createFacility_withZeroCapacity_throwsException() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                Facility.create("Lab A", FacilityType.COMPUTER_LAB, 0,
                        CS_BUILDING, STANDARD_HOURS, Set.of()))
                .withMessageContaining("INV-F3");
    }

    // ------------------------------------------------------------------
    // INV-F9: cannot create with RETIRED status
    // ------------------------------------------------------------------
        @Test
        void createFacility_alwaysStartsAsAvailable() {
                Facility facility = Facility.create("Lab A", FacilityType.COMPUTER_LAB, 30,
                CS_BUILDING, STANDARD_HOURS, Set.of());

        // INV-F9: factory method always sets AVAILABLE — RETIRED only reachable via updateStatus()
        assertThat(facility.getStatus()).isEqualTo(FacilityStatus.AVAILABLE);
        }

    // ------------------------------------------------------------------
    // INV-F4: RETIRED is terminal
    // ------------------------------------------------------------------
    @Test
    void retiredFacility_cannotTransitionToAvailable() {
        Facility facility = Facility.create("Lab A", FacilityType.COMPUTER_LAB, 30,
                CS_BUILDING, STANDARD_HOURS, Set.of());
        facility.pullDomainEvents(); // clear created event

        // Manually bring to AVAILABLE then to RETIRED
        facility.updateStatus(FacilityStatus.RETIRED, "decommissioned");

        assertThatIllegalStateException().isThrownBy(() ->
                facility.updateStatus(FacilityStatus.AVAILABLE, "reopen"))
                .withMessageContaining("INV-F4");
    }

    // ------------------------------------------------------------------
    // INV-F6: maintenance windows cannot overlap
    // ------------------------------------------------------------------
    @Test
    void addMaintenanceWindow_withOverlap_throwsException() {
        Facility facility = Facility.create("Sports Hall", FacilityType.SPORTS_AREA, 50,
                new Location("Sports Centre", 0, "Main Hall"), STANDARD_HOURS, Set.of());
        facility.pullDomainEvents();

        Instant start = Instant.parse("2026-04-10T09:00:00Z");
        Instant end   = Instant.parse("2026-04-10T17:00:00Z");
        facility.addMaintenanceWindow(UUID.randomUUID(), start, end, "Electrical");

        // Overlapping window
        Instant overlapStart = Instant.parse("2026-04-10T12:00:00Z");
        Instant overlapEnd   = Instant.parse("2026-04-10T20:00:00Z");

        assertThatIllegalStateException().isThrownBy(() ->
                facility.addMaintenanceWindow(UUID.randomUUID(), overlapStart, overlapEnd, "Overlapping"))
                .withMessageContaining("INV-F6");
    }

    // ------------------------------------------------------------------
    // INV-L5: maintenance window start must be before end
    // ------------------------------------------------------------------
    @Test
    void maintenanceWindow_withStartAfterEnd_throwsException() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new MaintenanceWindow(UUID.randomUUID(),
                        Instant.parse("2026-04-10T17:00:00Z"),
                        Instant.parse("2026-04-10T09:00:00Z"),
                        "Invalid"));
    }

    // ------------------------------------------------------------------
    // INV-F7: operating hours start must be before end
    // ------------------------------------------------------------------
    @Test
    void operatingHours_withInvalidRange_throwsException() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new OperatingHours(LocalTime.of(21, 0), LocalTime.of(9, 0),
                        Set.of(DayOfWeek.MONDAY)));
    }

    // ------------------------------------------------------------------
    // Happy path: domain events are produced on state changes
    // ------------------------------------------------------------------
    @Test
    void createFacility_producesFacilityCreatedEvent() {
        Facility facility = Facility.create("Lab B", FacilityType.COMPUTER_LAB, 25,
                CS_BUILDING, STANDARD_HOURS, Set.of("PROJECTOR"));

        var events = facility.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo("FacilityCreated");
    }

    @Test
    void updateStatus_producesFacilityStatusChangedEvent() {
        Facility facility = Facility.create("Lab C", FacilityType.COMPUTER_LAB, 25,
                CS_BUILDING, STANDARD_HOURS, Set.of());
        facility.pullDomainEvents();

        facility.updateStatus(FacilityStatus.MAINTENANCE, "Annual inspection");

        var events = facility.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo("FacilityStatusChanged");
    }

    @Test
    void scheduleMaintenance_producesMaintenanceScheduledEvent() {
        Facility facility = Facility.create("Lab D", FacilityType.COMPUTER_LAB, 25,
                CS_BUILDING, STANDARD_HOURS, Set.of());
        facility.pullDomainEvents();

        facility.addMaintenanceWindow(UUID.randomUUID(),
                Instant.parse("2026-04-15T09:00:00Z"),
                Instant.parse("2026-04-15T17:00:00Z"),
                "Inspection");

        var events = facility.pullDomainEvents();
        assertThat(events).anyMatch(e -> e.eventType().equals("MaintenanceScheduled"));
    }

    @Test
    void isBookable_returnsFalseWhenMaintenance() {
        Facility facility = Facility.create("Hall X", FacilityType.SPORTS_AREA, 100,
                new Location("Sports Centre", 0, "Hall X"), STANDARD_HOURS, Set.of());
        facility.pullDomainEvents();
        facility.updateStatus(FacilityStatus.MAINTENANCE, "Refurb");

        assertThat(facility.isBookable()).isFalse();
    }
}
