package com.plassey.facilityservice.application.dto;

import com.plassey.facilityservice.domain.model.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// ============================================================
// Request DTOs (Inbound ACL)
// ============================================================

public class FacilityDtos {

    public record CreateFacilityRequest(
            String name,
            String type,
            int capacity,
            LocationDto location,
            OperatingHoursDto operatingHours,
            Set<String> amenities
    ) {}

    public record UpdateFacilityRequest(
            String name,
            int capacity,
            LocationDto location,
            OperatingHoursDto operatingHours,
            Set<String> amenities
    ) {}

    public record UpdateStatusRequest(
            String status,
            String reason
    ) {}

    public record ScheduleMaintenanceRequest(
            String startTime,   // ISO-8601
            String endTime,
            String reason
    ) {}

    public record LocationDto(
            String building,
            int floor,
            String room
    ) {}

    public record OperatingHoursDto(
            String startTime,   // HH:mm
            String endTime,
            List<String> daysOfWeek
    ) {}

    // ============================================================
    // Response DTOs (Outbound ACL)
    // ============================================================

    public record FacilityResponse(
            String facilityId,
            String name,
            String type,
            String status,
            int capacity,
            LocationDto location,
            OperatingHoursDto operatingHours,
            List<String> amenities,
            List<MaintenanceWindowDto> maintenanceWindows,
            String createdAt,
            String updatedAt
    ) {}

    public record MaintenanceWindowDto(
            String windowId,
            String startTime,
            String endTime,
            String reason
    ) {}

    public record FacilityExistsResponse(
            boolean exists,
            String facilityId,
            String name,
            String status,
            boolean isBookable,
            String reason
    ) {}

    public record PagedResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    // ============================================================
    // Mapper helpers (inbound + outbound ACL transformations)
    // ============================================================

    public static Location toLocation(LocationDto dto) {
        if (dto == null) return null;
        return new Location(dto.building().trim(), dto.floor(), dto.room().trim());
    }

    public static OperatingHours toOperatingHours(OperatingHoursDto dto) {
        if (dto == null) return null;
        Set<DayOfWeek> days = new java.util.LinkedHashSet<>();
        if (dto.daysOfWeek() != null) {
            dto.daysOfWeek().forEach(d -> days.add(DayOfWeek.valueOf(d.toUpperCase())));
        }
        return new OperatingHours(LocalTime.parse(dto.startTime()), LocalTime.parse(dto.endTime()), days);
    }

    public static FacilityResponse toResponse(Facility f) {
        LocationDto loc = f.getLocation() != null
                ? new LocationDto(f.getLocation().building(), f.getLocation().floor(), f.getLocation().room())
                : null;

        OperatingHoursDto oh = f.getOperatingHours() != null
                ? new OperatingHoursDto(
                        f.getOperatingHours().startTime().toString(),
                        f.getOperatingHours().endTime().toString(),
                        f.getOperatingHours().daysOfWeek().stream().map(DayOfWeek::name).toList())
                : null;

        List<MaintenanceWindowDto> windows = f.getMaintenanceWindows().stream()
                .map(w -> new MaintenanceWindowDto(
                        w.getId().toString(),
                        w.getStartTime().toString(),
                        w.getEndTime().toString(),
                        w.getReason()))
                .toList();

        return new FacilityResponse(
                f.getId().toString(),
                f.getName(),
                f.getType().name(),
                f.getStatus().name(),
                f.getCapacity(),
                loc, oh,
                f.getAmenities().stream().toList(),
                windows,
                f.getCreatedAt().toString(),
                f.getUpdatedAt().toString()
        );
    }
}
