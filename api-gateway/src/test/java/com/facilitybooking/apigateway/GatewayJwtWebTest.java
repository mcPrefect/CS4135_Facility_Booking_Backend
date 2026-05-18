package com.facilitybooking.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayJwtWebTest {

    private static final String SECRET = "my-super-secret-key-which-is-long-enough-123456";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void actuatorHealthDoesNotRequireJwt() {
        webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void facilitiesWithoutBearerAreUnauthorized() {
        webTestClient.get().uri("/api/v1/facilities").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void facilitiesWithMalformedBearerAreUnauthorized() {
        webTestClient.get()
                .uri("/api/v1/facilities")
                .header("Authorization", "Bearer not-a-jwt")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void authLoginIsReachableWithoutJwt() {
        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"nobody@example.com\",\"password\":\"wrongpass\"}")
                .exchange()
                .expectStatus()
                .value(status -> assertThat(status).isIn(401, 500, 502, 503));
    }

    @Test
    void facilitiesWithValidJwtAreNotRejectedByGateway() {
        webTestClient.get()
                .uri("/api/v1/facilities?page=0&size=1")
                .header("Authorization", "Bearer " + validStudentToken())
                .exchange()
                .expectStatus()
                .value(status -> assertThat(status).isNotEqualTo(401));
    }

    @Test
    void bookingsWithoutBearerAreUnauthorized() {
        webTestClient.get().uri("/api/v1/bookings").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void bookingsWithValidJwtAreNotRejectedByGateway() {
        webTestClient.get()
                .uri("/api/v1/bookings")
                .header("Authorization", "Bearer " + validStudentToken())
                .exchange()
                .expectStatus()
                .value(status -> assertThat(status).isNotEqualTo(401));
    }

    private static String validStudentToken() {
        return Jwts.builder()
                .subject("u@test.com")
                .claim("userId", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
                .claim("role", "STUDENT")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
