package com.plassey.facilityservice.infrastructure.persistence.repository;

import com.plassey.facilityservice.domain.model.Facility;
import com.plassey.facilityservice.domain.model.FacilityStatus;
import com.plassey.facilityservice.domain.model.FacilityType;
import com.plassey.facilityservice.domain.repository.FacilityRepository;
import com.plassey.facilityservice.infrastructure.persistence.mapper.FacilityMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class FacilityRepositoryImpl implements FacilityRepository {

    private final FacilityJpaRepository jpa;

    public FacilityRepositoryImpl(FacilityJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Facility save(Facility facility) {
        var saved = jpa.save(FacilityMapper.toJpa(facility));
        return FacilityMapper.toDomain(saved);
    }

    @Override
    public Optional<Facility> findById(UUID id) {
        return jpa.findById(id).map(FacilityMapper::toDomain);
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        return jpa.existsByNameIgnoreCase(name);
    }

    @Override
    public boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id) {
        return jpa.existsByNameIgnoreCaseAndIdNot(name, id.toString());
    }

    @Override
    public List<Facility> search(FacilityType type, Integer minCapacity, Integer maxCapacity,
                                 FacilityStatus status, String building, int page, int size) {
        List<Facility> all = jpa.search(
                        type != null ? type.name() : null,
                        minCapacity, maxCapacity,
                        status != null ? status.name() : null,
                        building)
                .stream().map(FacilityMapper::toDomain).collect(Collectors.toList());

        int fromIndex = Math.min(page * size, all.size());
        int toIndex   = Math.min(fromIndex + size, all.size());
        return all.subList(fromIndex, toIndex);
    }

    @Override
    public long countSearch(FacilityType type, Integer minCapacity, Integer maxCapacity,
                            FacilityStatus status, String building) {
        return jpa.countSearch(
                type != null ? type.name() : null,
                minCapacity, maxCapacity,
                status != null ? status.name() : null,
                building);
    }

    @Override
    public List<Facility> findByNameContainingIgnoreCase(String nameFragment) {
        return jpa.findByNameContainingIgnoreCase(nameFragment)
                .stream().map(FacilityMapper::toDomain).collect(Collectors.toList());
    }
}