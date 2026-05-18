package com.facilitybooking.userservice.service;

import com.facilitybooking.userservice.domain.valueobject.Role;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                jwtService,
                "secretKey",
                "my-super-secret-key-which-is-long-enough-123456");
    }

    @Test
    void generateToken_includesUserIdClaimAndEmailSubject() {
        UUID userId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String token = jwtService.generateToken("student@ul.ie", Role.STUDENT, userId);

        Claims claims = jwtService.extractClaims(token);
        assertThat(claims.getSubject()).isEqualTo("student@ul.ie");
        assertThat(claims.get("userId", String.class)).isEqualTo(userId.toString());
        assertThat(claims.get("role").toString()).isEqualTo("STUDENT");
    }
}
