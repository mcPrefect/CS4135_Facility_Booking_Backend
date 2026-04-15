package com.plassey.facilityservice.infrastructure.config;

import com.plassey.facilityservice.application.dto.FacilityDtos.*;
import com.plassey.facilityservice.application.service.FacilityApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Seeds sample facilities on first startup in non-test profiles.
 * Skips seeding if data already exists (idempotent).
 */
@Component
@Profile("!test")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final FacilityApplicationService service;

    public DataSeeder(FacilityApplicationService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        try {
            seedFacility("Computer Lab 2.01", "COMPUTER_LAB", 30,
                    new LocationDto("CS Building", 2, "2.01"),
                    new OperatingHoursDto("09:00", "21:00",
                            List.of("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY")),
                    Set.of("PROJECTOR","WHITEBOARD","AIR_CONDITIONING"));

            seedFacility("Sports Hall A", "SPORTS_AREA", 100,
                    new LocationDto("Sports Centre", 0, "Main Hall"),
                    new OperatingHoursDto("07:00", "22:00",
                            List.of("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY")),
                    Set.of("BASKETBALL_HOOPS","SCOREBOARD","SEATING"));

            seedFacility("Seminar Room 1.05", "SEMINAR_ROOM", 20,
                    new LocationDto("Arts Building", 1, "1.05"),
                    new OperatingHoursDto("08:00", "20:00",
                            List.of("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY")),
                    Set.of("PROJECTOR","WHITEBOARD"));

            seedFacility("Study Room L101", "STUDY_ROOM", 8,
                    new LocationDto("Glucksman Library", 1, "L101"),
                    new OperatingHoursDto("08:00", "22:00",
                            List.of("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY")),
                    Set.of("WHITEBOARD","POWER_SOCKETS"));

            log.info("DataSeeder: sample facilities ready.");
        } catch (FacilityApplicationService.FacilityNameConflictException e) {
            log.info("DataSeeder: facilities already seeded, skipping.");
        } catch (Exception e) {
            log.warn("DataSeeder: unexpected error during seeding – {}", e.getMessage());
        }
    }

    private void seedFacility(String name, String type, int capacity,
                               LocationDto location, OperatingHoursDto hours, Set<String> amenities) {
        try {
            service.createFacility(new CreateFacilityRequest(name, type, capacity, location, hours, amenities), null);
            log.info("DataSeeder: created facility '{}'", name);
        } catch (FacilityApplicationService.FacilityNameConflictException ignored) {
            // already exists – idempotent
        }
    }
}
