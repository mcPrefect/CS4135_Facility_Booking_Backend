package com.plassey.facilityservice.infrastructure.persistence.entity;

import com.plassey.facilityservice.domain.model.FacilityStatus;
import com.plassey.facilityservice.domain.model.FacilityType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(
    name = "facilities",
    uniqueConstraints = @UniqueConstraint(name = "uq_facility_name", columnNames = "name")
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FacilityJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    
    @Version
    private Long version;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private FacilityType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FacilityStatus status;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    // Location embedded columns
    @Column(name = "location_building")
    private String locationBuilding;

    @Column(name = "location_floor")
    private int locationFloor;

    @Column(name = "location_room")
    private String locationRoom;

    // Operating hours
    @Column(name = "op_start_time")
    private String opStartTime;   // HH:mm

    @Column(name = "op_end_time")
    private String opEndTime;     // HH:mm

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "facility_operating_days",
            joinColumns = @JoinColumn(name = "facility_id"))
    @Column(name = "day_of_week")
    private Set<String> operatingDays = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "facility_amenities",
            joinColumns = @JoinColumn(name = "facility_id"))
    @Column(name = "amenity")
    private Set<String> amenities = new LinkedHashSet<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "facility_id")
    private List<MaintenanceWindowJpaEntity> maintenanceWindows = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
