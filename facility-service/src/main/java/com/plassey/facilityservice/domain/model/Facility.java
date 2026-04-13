package com.plassey.facilityservice.domain.model;

import com.plassey.facilityservice.domain.events.*;

import java.time.Instant;
import java.util.*;

/**
 * Facility Aggregate Root.
 *
 * All state mutations go through this class. No external service may modify
 * a Facility directly. Invariants are enforced here.
 *
 * Business Invariants:
 *   INV-F1: name must be non-null and non-blank
 *   INV-F2: type is immutable after creation
 *   INV-F3: capacity > 0
 *   INV-L4: RETIRED is a terminal state
 *   INV-L5/F6: maintenance windows validated on addition
 *   INV-F8: name uniqueness enforced at repository level
 *   INV-F9: cannot be created with RETIRED status
 */
public class Facility {

    private final UUID id;
    private String name;
    private final FacilityType type;   // INV-F2: immutable
    private FacilityStatus status;
    private int capacity;              // raw int, validated via Capacity VO at boundary
    private Location location;
    private OperatingHours operatingHours;
    private final Set<String> amenities;
    private final List<MaintenanceWindow> maintenanceWindows;
    private final Instant createdAt;
    private Instant updatedAt;

    // Domain events collected for dispatch after successful persistence
    private final List<FacilityDomainEvent> domainEvents = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Factory method (create)
    // -----------------------------------------------------------------------

    public static Facility create(
            String name,
            FacilityType type,
            int capacity,
            Location location,
            OperatingHours operatingHours,
            Set<String> amenities) {

        // INV-F9: initial status can only be AVAILABLE — RETIRED is only reachable via updateStatus()
        if (type == null) throw new IllegalArgumentException("FacilityType must not be null");

        Facility facility = new Facility(
                UUID.randomUUID(), name, type, FacilityStatus.AVAILABLE,
                capacity, location, operatingHours,
                amenities == null ? new LinkedHashSet<>() : new LinkedHashSet<>(amenities),
                new ArrayList<>(), Instant.now());

        facility.domainEvents.add(new FacilityCreatedEvent(
                facility.id, facility.name, facility.type, facility.capacity,
                facility.location, Instant.now()));

        return facility;
    }

    // -----------------------------------------------------------------------
    // Constructor (reconstitution from persistence)
    // -----------------------------------------------------------------------

    public Facility(UUID id, String name, FacilityType type, FacilityStatus status,
                    int capacity, Location location, OperatingHours operatingHours,
                    Set<String> amenities, List<MaintenanceWindow> maintenanceWindows,
                    Instant createdAt) {
        validateName(name);
        validateCapacity(capacity);
        Objects.requireNonNull(type, "FacilityType must not be null");
        Objects.requireNonNull(status, "FacilityStatus must not be null");
        this.id = Objects.requireNonNull(id, "Facility ID must not be null");
        this.name = name.trim();
        this.type = type;
        this.status = status;
        this.capacity = capacity;
        this.location = location;
        this.operatingHours = operatingHours;
        this.amenities = amenities != null ? new LinkedHashSet<>(amenities) : new LinkedHashSet<>();
        this.maintenanceWindows = maintenanceWindows != null ? new ArrayList<>(maintenanceWindows) : new ArrayList<>();
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    // -----------------------------------------------------------------------
    // State mutation methods
    // -----------------------------------------------------------------------

    public void updateName(String name) {
        validateName(name);
        this.name = name.trim();
        this.updatedAt = Instant.now();
        domainEvents.add(new FacilityUpdatedEvent(this.id, Map.of("name", this.name), Instant.now()));
    }

    public void updateCapacity(int capacity) {
        validateCapacity(capacity);
        this.capacity = capacity;
        this.updatedAt = Instant.now();
        domainEvents.add(new FacilityUpdatedEvent(this.id, Map.of("capacity", String.valueOf(capacity)), Instant.now()));
    }

    public void updateLocation(Location location) {
        Objects.requireNonNull(location, "Location must not be null");
        this.location = location;
        this.updatedAt = Instant.now();
    }

    public void updateOperatingHours(OperatingHours operatingHours) {
        Objects.requireNonNull(operatingHours, "OperatingHours must not be null");
        this.operatingHours = operatingHours;
        this.updatedAt = Instant.now();
    }

    public void updateAmenities(Set<String> newAmenities) {
        this.amenities.clear();
        if (newAmenities != null) this.amenities.addAll(newAmenities);
        this.updatedAt = Instant.now();
    }

    /**
     * Status transition with invariant enforcement.
     * INV-L4: RETIRED is terminal.
     */
    public void updateStatus(FacilityStatus newStatus, String reason) {
        Objects.requireNonNull(newStatus, "New status must not be null");
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    String.format("Cannot transition from %s to %s (INV-L4)", this.status, newStatus));
        }
        FacilityStatus oldStatus = this.status;
        this.status = newStatus;
        this.updatedAt = Instant.now();
        domainEvents.add(new FacilityStatusChangedEvent(this.id, this.name, oldStatus, newStatus, reason, Instant.now()));
    }

    /**
     * Adds a maintenance window.
     * INV-L5: startTime must precede endTime (enforced in MaintenanceWindow constructor).
     * INV-F6: No overlapping windows for the same facility.
     */
    public MaintenanceWindow addMaintenanceWindow(UUID windowId, java.time.Instant startTime,
                                                   java.time.Instant endTime, String reason) {
        MaintenanceWindow window = new MaintenanceWindow(windowId, startTime, endTime, reason);

        boolean overlaps = maintenanceWindows.stream().anyMatch(existing -> existing.overlapsWith(window));
        if (overlaps) {
            throw new IllegalStateException(
                    "New maintenance window overlaps with an existing one for facility " + this.id + " (INV-F6)");
        }

        maintenanceWindows.add(window);
        this.updatedAt = Instant.now();
        domainEvents.add(new MaintenanceScheduledEvent(this.id, windowId, startTime, endTime, reason, Instant.now()));
        return window;
    }

    public boolean isBookable() {
        return this.status == FacilityStatus.AVAILABLE;
    }

    public boolean isUnderMaintenanceAt(Instant moment) {
        return maintenanceWindows.stream().anyMatch(w -> w.isActiveAt(moment));
    }

    // -----------------------------------------------------------------------
    // Domain event support
    // -----------------------------------------------------------------------

    public List<FacilityDomainEvent> pullDomainEvents() {
        List<FacilityDomainEvent> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return events;
    }

    // -----------------------------------------------------------------------
    // Validation helpers
    // -----------------------------------------------------------------------

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Facility name must not be null or blank (INV-F1)");
        }
    }

    private static void validateCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0 (INV-F3)");
        }
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    public UUID getId() { return id; }
    public String getName() { return name; }
    public FacilityType getType() { return type; }
    public FacilityStatus getStatus() { return status; }
    public int getCapacity() { return capacity; }
    public Location getLocation() { return location; }
    public OperatingHours getOperatingHours() { return operatingHours; }
    public Set<String> getAmenities() { return Collections.unmodifiableSet(amenities); }
    public List<MaintenanceWindow> getMaintenanceWindows() { return Collections.unmodifiableList(maintenanceWindows); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}