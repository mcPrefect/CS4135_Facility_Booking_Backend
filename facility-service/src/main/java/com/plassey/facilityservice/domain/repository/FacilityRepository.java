package com.plassey.facilityservice.domain.repository;

import com.plassey.facilityservice.domain.model.Facility;
import com.plassey.facilityservice.domain.model.FacilityStatus;
import com.plassey.facilityservice.domain.model.FacilityType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain-level repository interface for the Facility aggregate root.
 * Infrastructure (JPA) implements this; the domain has no JPA dependency.
 */
public interface FacilityRepository {

    Facility save(Facility facility);

    Optional<Facility> findById(UUID id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    List<Facility> search(FacilityType type, Integer minCapacity, Integer maxCapacity,
                          FacilityStatus status, String building, int page, int size);

    long countSearch(FacilityType type, Integer minCapacity, Integer maxCapacity,
                     FacilityStatus status, String building);

    List<Facility> findByNameContainingIgnoreCase(String nameFragment);
}
