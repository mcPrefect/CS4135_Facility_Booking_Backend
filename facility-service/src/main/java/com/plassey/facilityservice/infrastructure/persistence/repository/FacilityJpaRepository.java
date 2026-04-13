package com.plassey.facilityservice.infrastructure.persistence.repository;

import com.plassey.facilityservice.domain.model.FacilityStatus;
import com.plassey.facilityservice.domain.model.FacilityType;
import com.plassey.facilityservice.infrastructure.persistence.entity.FacilityJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FacilityJpaRepository extends JpaRepository<FacilityJpaEntity, UUID> {

    // Native queries bypass Hibernate type inference – avoids lower(bytea) / ~~* bytea errors
    // that occur with JPQL + PostgreSQL driver parameter binding

    @Query(value = "SELECT COUNT(*) > 0 FROM facilities WHERE name ILIKE :name",
            nativeQuery = true)
    boolean existsByNameIgnoreCase(@Param("name") String name);

    @Query(value = "SELECT COUNT(*) > 0 FROM facilities WHERE name ILIKE :name AND CAST(id AS VARCHAR) <> :id",
            nativeQuery = true)
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") String id);

    @Query(value = "SELECT * FROM facilities WHERE name ILIKE CONCAT('%', :fragment, '%')",
            nativeQuery = true)
    List<FacilityJpaEntity> findByNameContainingIgnoreCase(@Param("fragment") String nameFragment);

    @Query(value = """
        SELECT * FROM facilities
        WHERE (:type IS NULL OR type = CAST(:type AS VARCHAR))
          AND (:minCapacity IS NULL OR capacity >= CAST(:minCapacity AS INTEGER))
          AND (:maxCapacity IS NULL OR capacity <= CAST(:maxCapacity AS INTEGER))
          AND (:status IS NULL OR status = CAST(:status AS VARCHAR))
          AND (:building IS NULL OR location_building ILIKE CONCAT('%', :building, '%'))
          AND status <> 'RETIRED'
        ORDER BY name ASC
        """, nativeQuery = true)
    List<FacilityJpaEntity> search(
            @Param("type") String type,
            @Param("minCapacity") Integer minCapacity,
            @Param("maxCapacity") Integer maxCapacity,
            @Param("status") String status,
            @Param("building") String building
    );

    @Query(value = """
        SELECT COUNT(*) FROM facilities
        WHERE (:type IS NULL OR type = CAST(:type AS VARCHAR))
          AND (:minCapacity IS NULL OR capacity >= CAST(:minCapacity AS INTEGER))
          AND (:maxCapacity IS NULL OR capacity <= CAST(:maxCapacity AS INTEGER))
          AND (:status IS NULL OR status = CAST(:status AS VARCHAR))
          AND (:building IS NULL OR location_building ILIKE CONCAT('%', :building, '%'))
          AND status <> 'RETIRED'
        """, nativeQuery = true)
    long countSearch(
            @Param("type") String type,
            @Param("minCapacity") Integer minCapacity,
            @Param("maxCapacity") Integer maxCapacity,
            @Param("status") String status,
            @Param("building") String building
    );
}