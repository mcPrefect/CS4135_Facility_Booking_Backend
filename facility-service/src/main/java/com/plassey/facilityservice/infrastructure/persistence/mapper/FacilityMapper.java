package com.plassey.facilityservice.infrastructure.persistence.mapper;

import com.plassey.facilityservice.domain.model.*;
import com.plassey.facilityservice.infrastructure.persistence.entity.FacilityJpaEntity;
import com.plassey.facilityservice.infrastructure.persistence.entity.MaintenanceWindowJpaEntity;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FacilityMapper {

    private FacilityMapper() {}

    public static FacilityJpaEntity toJpa(Facility domain) {
        OperatingHours oh = domain.getOperatingHours();

        List<MaintenanceWindowJpaEntity> windows = domain.getMaintenanceWindows().stream()
                .map(w -> MaintenanceWindowJpaEntity.builder()
                        .id(w.getId())
                        .facilityId(domain.getId())
                        .startTime(w.getStartTime())
                        .endTime(w.getEndTime())
                        .reason(w.getReason())
                        .build())
                .collect(Collectors.toList());

        Set<String> days = oh == null ? new LinkedHashSet<>() :
                oh.daysOfWeek().stream().map(DayOfWeek::name).collect(Collectors.toCollection(LinkedHashSet::new));

        return FacilityJpaEntity.builder()
                .id(domain.getId())
                .name(domain.getName())
                .type(domain.getType())
                .status(domain.getStatus())
                .capacity(domain.getCapacity())
                .locationBuilding(domain.getLocation() != null ? domain.getLocation().building() : null)
                .locationFloor(domain.getLocation() != null ? domain.getLocation().floor() : 0)
                .locationRoom(domain.getLocation() != null ? domain.getLocation().room() : null)
                .opStartTime(oh != null ? oh.startTime().toString() : null)
                .opEndTime(oh != null ? oh.endTime().toString() : null)
                .operatingDays(days)
                .amenities(new LinkedHashSet<>(domain.getAmenities()))
                .maintenanceWindows(windows)
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    public static Facility toDomain(FacilityJpaEntity jpa) {
        Location location = jpa.getLocationBuilding() != null
                ? new Location(jpa.getLocationBuilding(), jpa.getLocationFloor(), jpa.getLocationRoom())
                : null;

        OperatingHours oh = null;
        if (jpa.getOpStartTime() != null && jpa.getOpEndTime() != null) {
            Set<DayOfWeek> days = jpa.getOperatingDays().stream()
                    .map(DayOfWeek::valueOf)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            oh = new OperatingHours(LocalTime.parse(jpa.getOpStartTime()), LocalTime.parse(jpa.getOpEndTime()), days);
        }

        List<MaintenanceWindow> windows = jpa.getMaintenanceWindows().stream()
                .map(w -> new MaintenanceWindow(w.getId(), w.getStartTime(), w.getEndTime(), w.getReason()))
                .collect(Collectors.toList());

        return new Facility(
                jpa.getId(), jpa.getName(), jpa.getType(), jpa.getStatus(),
                jpa.getCapacity(), location, oh,
                new LinkedHashSet<>(jpa.getAmenities()), windows, jpa.getCreatedAt()
        );
    }
}
