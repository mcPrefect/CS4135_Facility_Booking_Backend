package com.plassey.facilityservice.api.controller;

import com.plassey.facilityservice.application.dto.FacilityDtos.*;
import com.plassey.facilityservice.application.service.FacilityApplicationService;
import com.plassey.facilityservice.application.service.FacilityApplicationService.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Facility bounded context.
 * All endpoints versioned under /api/v1/ (NFR-17).
 */
@RestController
@RequestMapping("/api/v1/facilities")
public class FacilityController {

    private final FacilityApplicationService service;

    public FacilityController(FacilityApplicationService service) {
        this.service = service;
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/facilities  – search with filters (any authenticated user)
    // -----------------------------------------------------------------------
    @GetMapping
    public ResponseEntity<PagedResponse<FacilityResponse>> search(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) Integer maxCapacity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String building,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                service.searchFacilities(type, minCapacity, maxCapacity, status, building, page, size));
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/facilities/{id}  – full facility details
    // -----------------------------------------------------------------------
    @GetMapping("/{facilityId}")
    public ResponseEntity<FacilityResponse> getById(@PathVariable UUID facilityId) {
        return ResponseEntity.ok(service.getFacility(facilityId));
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/facilities/{id}/exists  – lightweight validation for S2S
    // -----------------------------------------------------------------------
    @GetMapping("/{facilityId}/exists")
    public ResponseEntity<FacilityExistsResponse> exists(@PathVariable UUID facilityId) {
        FacilityExistsResponse result = service.checkExists(facilityId);
        // Returns 200 whether found or not (body contains exists flag) – per contract
        return ResponseEntity.ok(result);
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/facilities/lookup/batch  – NLP batch lookup by name
    // -----------------------------------------------------------------------
    @GetMapping("/lookup/batch")
    public ResponseEntity<List<FacilityResponse>> batchLookup(
            @RequestParam List<String> names) {
        return ResponseEntity.ok(service.batchLookupByName(names));
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/facilities  – create (ADMIN only)
    // -----------------------------------------------------------------------
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FacilityResponse> create(
            @RequestBody CreateFacilityRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        FacilityResponse response = service.createFacility(req, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -----------------------------------------------------------------------
    // PUT /api/v1/facilities/{id}  – update metadata (ADMIN only)
    // -----------------------------------------------------------------------
    @PutMapping("/{facilityId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FacilityResponse> update(
            @PathVariable UUID facilityId,
            @RequestBody UpdateFacilityRequest req) {

        return ResponseEntity.ok(service.updateFacility(facilityId, req));
    }

    // -----------------------------------------------------------------------
    // PATCH /api/v1/facilities/{id}/status  – status transition (ADMIN only)
    // -----------------------------------------------------------------------
    @PatchMapping("/{facilityId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FacilityResponse> updateStatus(
            @PathVariable UUID facilityId,
            @RequestBody UpdateStatusRequest req) {

        return ResponseEntity.ok(service.updateStatus(facilityId, req));
    }

    // -----------------------------------------------------------------------
    // DELETE /api/v1/facilities/{id}  – soft-delete / retire (ADMIN only)
    // -----------------------------------------------------------------------
    @DeleteMapping("/{facilityId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID facilityId) {
        service.deleteFacility(facilityId);
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/facilities/{id}/maintenance  – schedule maintenance (ADMIN)
    // -----------------------------------------------------------------------
    @PostMapping("/{facilityId}/maintenance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FacilityResponse> scheduleMaintenance(
            @PathVariable UUID facilityId,
            @RequestBody ScheduleMaintenanceRequest req) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.scheduleMaintenance(facilityId, req));
    }
}
