package com.facilitybooking.userservice;

import com.facilitybooking.userservice.domain.entity.User;
import com.facilitybooking.userservice.domain.valueobject.Role;
import com.facilitybooking.userservice.dto.RegisterRequestDTO;
import com.facilitybooking.userservice.messaging.UserEventPublisher;
import com.facilitybooking.userservice.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
//@Import(TestConfig.class)
public class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private UserEventPublisher userEventPublisher;


    @Test
    void register_shouldReturn200_whenRequestValid() throws Exception {
        RegisterRequestDTO registerRequestDTO = new RegisterRequestDTO();
        registerRequestDTO.setEmail("user@example.com");
        registerRequestDTO.setPassword("12345678");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequestDTO)))
                    .andExpect(status().isOk());

    }

    @Test
    void register_shouldFail_whenDuplicateEmail() throws Exception {
        RegisterRequestDTO registerRequestDTO = new RegisterRequestDTO();
        registerRequestDTO.setEmail("user@example.com");
        registerRequestDTO.setPassword("12345678");

        // First succeeds
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequestDTO)))
                .andExpect(status().isOk());

        // Second should fail
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequestDTO)))
                .andExpect(status().is4xxClientError());

    }

    @Test
    void login_shouldReturn200_whenCredentialValid() throws Exception {
        RegisterRequestDTO registerRequestDTO = new RegisterRequestDTO();
        registerRequestDTO.setEmail("user@example.com");
        registerRequestDTO.setPassword("12345678");

        // Register first
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequestDTO)))
                .andExpect(status().isOk());

        String loginRequest = """
                {
                    "email": "user@example.com",
                    "password": "12345678"
                }
                """;

        // Then login
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());

    }

    @Test
    void login_shouldFail_whenCredentialInValid() throws Exception {
        RegisterRequestDTO registerRequestDTO = new RegisterRequestDTO();
        registerRequestDTO.setEmail("user@example.com");
        registerRequestDTO.setPassword("12345678");

        // Register first
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequestDTO)))
                .andExpect(status().isOk());

        String loginRequest = """
                {
                    "email": "user@example.com",
                    "password": "87654321"
                }
                """;

        // Then login
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

    }

    @Test
    void getUserCount_shouldReturn200_withTokenAndRoleAdmin() throws Exception {
        User admin = new User("admin@example.com", passwordEncoder.encode("12345678"), Role.ADMIN);
        userRepository.save(admin);

        String loginRequest = """
                {
                    "email": "admin@example.com",
                    "password": "12345678"
                }
                """;

        // Then login
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).get("token").asText();

        mockMvc.perform(get("/api/v1/admin/users/count")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));


    }

    @Test
    void getUserCount_shouldReturn401_withoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/count"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void getUserCount_shouldReturn403_withNonAdminToken() throws Exception {
        RegisterRequestDTO registerRequestDTO = new RegisterRequestDTO();
        registerRequestDTO.setEmail("user@example.com");
        registerRequestDTO.setPassword("12345678");

        // Register non-admin user
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequestDTO)))
                .andExpect(status().isOk());

        String loginRequest = """
                {
                    "email": "user@example.com",
                    "password": "12345678"
                }
                """;

        // login and capture token
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).get("token").asText();

        // call admin endpoint with non-admin token
        mockMvc.perform(get("/api/v1/admin/users/count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_shouldPublishUserRegisteredEvent() throws Exception {
        RegisterRequestDTO registerRequestDTO = new RegisterRequestDTO();
        registerRequestDTO.setEmail("user@example.com");
        registerRequestDTO.setPassword("12345678");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequestDTO)))
                .andExpect(status().isOk());

        verify(userEventPublisher).publishUserRegistered(any());
    }


}
