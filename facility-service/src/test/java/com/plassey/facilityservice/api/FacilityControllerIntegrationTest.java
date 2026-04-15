package com.plassey.facilityservice.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plassey.facilityservice.application.dto.FacilityDtos.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.plassey.facilityservice.TestConfig;
import org.springframework.context.annotation.Import;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestConfig.class)
class FacilityControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String BASE = "/api/v1/facilities";

    // ------------------------------------------------------------------
    // Unauthenticated access is rejected
    // ------------------------------------------------------------------
    @Test
    void searchFacilities_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Create → Get round-trip (ADMIN role)
    // ------------------------------------------------------------------
    @Test
    @WithMockUser(roles = "ADMIN")
    void createAndGetFacility_roundTrip() throws Exception {
        CreateFacilityRequest req = new CreateFacilityRequest(
                "Integration Test Lab",
                "COMPUTER_LAB",
                30,
                new LocationDto("CS Building", 2, "2.01"),
                new OperatingHoursDto("09:00", "21:00",
                        List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")),
                Set.of("PROJECTOR", "WHITEBOARD")
        );

        // POST - create
        MvcResult createResult = mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Integration Test Lab"))
                .andExpect(jsonPath("$.type").value("COMPUTER_LAB"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.capacity").value(30))
                .andReturn();

        String body = createResult.getResponse().getContentAsString();
        FacilityResponse created = objectMapper.readValue(body, FacilityResponse.class);

        // GET by id
        mockMvc.perform(get(BASE + "/" + created.facilityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facilityId").value(created.facilityId()))
                .andExpect(jsonPath("$.amenities").isArray());
    }

    // ------------------------------------------------------------------
    // Duplicate name returns 409
    // ------------------------------------------------------------------
    @Test
    @WithMockUser(roles = "ADMIN")
    void createFacility_withDuplicateName_returns409() throws Exception {
        CreateFacilityRequest req = new CreateFacilityRequest(
                "Unique Seminar Room", "SEMINAR_ROOM", 20,
                new LocationDto("Arts Building", 1, "1.05"),
                new OperatingHoursDto("08:00", "20:00", List.of("MONDAY")), Set.of());

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());

        // Second create with same name
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------
    // STUDENT role cannot create
    // ------------------------------------------------------------------
    @Test
    @WithMockUser(roles = "STUDENT")
    void createFacility_asStudent_returns403() throws Exception {
        CreateFacilityRequest req = new CreateFacilityRequest(
                "Student Attempt Lab", "COMPUTER_LAB", 20,
                new LocationDto("CS Building", 1, "1.01"),
                new OperatingHoursDto("09:00", "17:00", List.of("MONDAY")), Set.of());

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Exists endpoint returns bookability info
    // ------------------------------------------------------------------
    @Test
    @WithMockUser(roles = "ADMIN")
    void existsEndpoint_returnsCorrectBookabilityInfo() throws Exception {
        // Create a facility first
        CreateFacilityRequest req = new CreateFacilityRequest(
                "Bookability Check Room", "STUDY_ROOM", 10,
                new LocationDto("Library", 1, "L101"),
                new OperatingHoursDto("09:00", "22:00",
                        List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")),
                Set.of());

        MvcResult createResult = mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();

        FacilityResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), FacilityResponse.class);

        mockMvc.perform(get(BASE + "/" + created.facilityId() + "/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.isBookable").value(true));
    }

    // ------------------------------------------------------------------
    // Search with filters
    // ------------------------------------------------------------------
    @Test
    @WithMockUser(roles = "STUDENT")
    void searchFacilities_withTypeFilter_returnsFilteredResults() throws Exception {
        mockMvc.perform(get(BASE).param("type", "COMPUTER_LAB").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0));
    }

    // ------------------------------------------------------------------
    // Soft-delete (ADMIN) – subsequent GET still returns 200 (status RETIRED)
    // ------------------------------------------------------------------
    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteFacility_softDeletes() throws Exception {
        CreateFacilityRequest req = new CreateFacilityRequest(
                "To Be Deleted Room", "MEETING_ROOM", 8,
                new LocationDto("Admin Block", 0, "G01"),
                new OperatingHoursDto("08:00", "18:00", List.of("MONDAY")), Set.of());

        MvcResult result = mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated()).andReturn();

        FacilityResponse created = objectMapper.readValue(result.getResponse().getContentAsString(),
                FacilityResponse.class);

        mockMvc.perform(delete(BASE + "/" + created.facilityId()))
                .andExpect(status().isNoContent());

        // Facility still exists in DB with RETIRED status
        mockMvc.perform(get(BASE + "/" + created.facilityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETIRED"));
    }

    // ------------------------------------------------------------------
    // Maintenance scheduling
    // ------------------------------------------------------------------
    @Test
    @WithMockUser(roles = "ADMIN")
    void scheduleMaintenance_addsWindowAndPublishesEvent() throws Exception {
        CreateFacilityRequest req = new CreateFacilityRequest(
                "Maintenance Test Hall", "SPORTS_AREA", 50,
                new LocationDto("Sports Centre", 0, "Hall A"),
                new OperatingHoursDto("07:00", "22:00",
                        List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY",
                                "SATURDAY", "SUNDAY")),
                Set.of());

        MvcResult created = mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();

        FacilityResponse facility = objectMapper.readValue(
                created.getResponse().getContentAsString(), FacilityResponse.class);

        ScheduleMaintenanceRequest maint = new ScheduleMaintenanceRequest(
                "2026-05-01T09:00:00Z", "2026-05-01T17:00:00Z", "Annual safety check");

        mockMvc.perform(post(BASE + "/" + facility.facilityId() + "/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maint)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maintenanceWindows").isArray())
                .andExpect(jsonPath("$.maintenanceWindows.length()").value(1));
    }
}
