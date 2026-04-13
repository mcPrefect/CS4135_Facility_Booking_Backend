package com.plassey.facilityservice.application.service;

import com.plassey.facilityservice.application.dto.FacilityDtos;
import com.plassey.facilityservice.application.dto.FacilityDtos.*;
import com.plassey.facilityservice.domain.model.*;
import com.plassey.facilityservice.domain.repository.FacilityRepository;
import com.plassey.facilityservice.domain.service.FacilityValidationService;
import com.plassey.facilityservice.infrastructure.messaging.FacilityEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FacilityApplicationService {

    private static final Logger log = LoggerFactory.getLogger(FacilityApplicationService.class);

    private final FacilityRepository facilityRepository;
    private final FacilityValidationService validationService;
    private final FacilityEventPublisher eventPublisher;

    public FacilityApplicationService(FacilityRepository facilityRepository,
                                      FacilityValidationService validationService,
                                      FacilityEventPublisher eventPublisher) {
        this.facilityRepository = facilityRepository;
        this.validationService  = validationService;
        this.eventPublisher     = eventPublisher;
    }

    // -----------------------------------------------------------------------
    // Create
    // -----------------------------------------------------------------------

    public FacilityResponse createFacility(CreateFacilityRequest req, String idempotencyKey) {
        // Inbound ACL: validate and translate
        String normalizedName = normalizeName(req.name());
        FacilityType type = parseFacilityType(req.type());

        if (validationService.isNameTaken(normalizedName, null)) {
            throw new FacilityNameConflictException("Facility name '" + normalizedName + "' already exists");
        }

        Location location      = FacilityDtos.toLocation(req.location());
        OperatingHours oh      = FacilityDtos.toOperatingHours(req.operatingHours());

        Facility facility = Facility.create(normalizedName, type, req.capacity(), location, oh, req.amenities());
        Facility saved    = facilityRepository.save(facility);

        // Dispatch collected domain events after successful save
        saved.pullDomainEvents().forEach(eventPublisher::publish);

        log.info("Facility created: id={} name={}", saved.getId(), saved.getName());
        return FacilityDtos.toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public FacilityResponse getFacility(UUID id) {
        return facilityRepository.findById(id)
                .map(FacilityDtos::toResponse)
                .orElseThrow(() -> new FacilityNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<FacilityResponse> searchFacilities(
            String type, Integer minCapacity, Integer maxCapacity,
            String status, String building, int page, int size) {

        FacilityType  ft = type   != null ? parseFacilityType(type)   : null;
        FacilityStatus fs = status != null ? parseFacilityStatus(status) : null;

        List<FacilityResponse> results = facilityRepository
                .search(ft, minCapacity, maxCapacity, fs, building, page, size)
                .stream().map(FacilityDtos::toResponse).toList();

        long total = facilityRepository.countSearch(ft, minCapacity, maxCapacity, fs, building);
        int totalPages = (int) Math.ceil((double) total / size);

        return new PagedResponse<>(results, page, size, total, totalPages);
    }

    @Transactional(readOnly = true)
    public FacilityExistsResponse checkExists(UUID id) {
        FacilityValidationService.ValidationResult result =
                validationService.validateBookability(id, null);
        return new FacilityExistsResponse(
                result.exists(), result.facilityId().toString(),
                result.name(), result.status() != null ? result.status().name() : null,
                result.isBookable(), result.reason());
    }

    @Transactional(readOnly = true)
    public List<FacilityResponse> batchLookupByName(List<String> names) {
        return names.stream()
                .flatMap(name -> facilityRepository.findByNameContainingIgnoreCase(name).stream())
                .distinct()
                .map(FacilityDtos::toResponse)
                .toList();
    }

    // -----------------------------------------------------------------------
    // Update
    // -----------------------------------------------------------------------

    public FacilityResponse updateFacility(UUID id, UpdateFacilityRequest req) {
        Facility facility = loadOrThrow(id);
        String normalizedName = normalizeName(req.name());

        if (validationService.isNameTaken(normalizedName, id)) {
            throw new FacilityNameConflictException("Facility name '" + normalizedName + "' already exists");
        }

        facility.updateName(normalizedName);
        facility.updateCapacity(req.capacity());
        if (req.location() != null)      facility.updateLocation(FacilityDtos.toLocation(req.location()));
        if (req.operatingHours() != null) facility.updateOperatingHours(FacilityDtos.toOperatingHours(req.operatingHours()));
        if (req.amenities() != null)     facility.updateAmenities(req.amenities());

        Facility saved = facilityRepository.save(facility);
        saved.pullDomainEvents().forEach(eventPublisher::publish);
        return FacilityDtos.toResponse(saved);
    }

    public FacilityResponse updateStatus(UUID id, UpdateStatusRequest req) {
        Facility facility  = loadOrThrow(id);
        FacilityStatus target = parseFacilityStatus(req.status());
        facility.updateStatus(target, req.reason());

        Facility saved = facilityRepository.save(facility);
        saved.pullDomainEvents().forEach(eventPublisher::publish);
        return FacilityDtos.toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Delete (soft – sets status to RETIRED)
    // -----------------------------------------------------------------------

    public void deleteFacility(UUID id) {
        Facility facility = loadOrThrow(id);
        if (facility.getStatus() == FacilityStatus.RETIRED) return; // idempotent
        facility.updateStatus(FacilityStatus.RETIRED, "Soft-deleted by administrator");
        Facility saved = facilityRepository.save(facility);
        saved.pullDomainEvents().forEach(eventPublisher::publish);
        log.info("Facility soft-deleted: id={}", id);
    }

    // -----------------------------------------------------------------------
    // Maintenance
    // -----------------------------------------------------------------------

    public FacilityResponse scheduleMaintenance(UUID id, ScheduleMaintenanceRequest req) {
        Facility facility = loadOrThrow(id);
        Instant start = Instant.parse(req.startTime());
        Instant end   = Instant.parse(req.endTime());

        facility.addMaintenanceWindow(UUID.randomUUID(), start, end, req.reason());
        Facility saved = facilityRepository.save(facility);
        saved.pullDomainEvents().forEach(eventPublisher::publish);
        return FacilityDtos.toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private Facility loadOrThrow(UUID id) {
        return facilityRepository.findById(id).orElseThrow(() -> new FacilityNotFoundException(id));
    }

    private static String normalizeName(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Name must not be blank");
        String trimmed = raw.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    private static FacilityType parseFacilityType(String raw) {
        try {
            return FacilityType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidFacilityTypeException("Unknown facility type: " + raw);
        }
    }

    private static FacilityStatus parseFacilityStatus(String raw) {
        try {
            return FacilityStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidFacilityStatusException("Unknown facility status: " + raw);
        }
    }

    // -----------------------------------------------------------------------
    // Domain-specific exceptions
    // -----------------------------------------------------------------------

    public static class FacilityNotFoundException extends RuntimeException {
        public FacilityNotFoundException(UUID id) { super("Facility not found: " + id); }
    }

    public static class FacilityNameConflictException extends RuntimeException {
        public FacilityNameConflictException(String msg) { super(msg); }
    }

    public static class InvalidFacilityTypeException extends RuntimeException {
        public InvalidFacilityTypeException(String msg) { super(msg); }
    }

    public static class InvalidFacilityStatusException extends RuntimeException {
        public InvalidFacilityStatusException(String msg) { super(msg); }
    }
}
