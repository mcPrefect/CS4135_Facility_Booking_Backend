package com.facilitybooking.bookingservice.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

  private static final String SECRET = "my-super-secret-key-which-is-long-enough-123456";
  private static final UUID USER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

  @Mock private FilterChain filterChain;

  private JwtAuthFilter filter;

  @BeforeEach
  void setUp() {
    filter = new JwtAuthFilter();
    ReflectionTestUtils.setField(filter, "jwtSecret", SECRET);
    SecurityContextHolder.clearContext();
  }

  @Test
  void usesUserIdClaimAsPrincipalNotEmailSubject() throws Exception {
    String token =
        Jwts.builder()
            .setSubject("student@ul.ie")
            .claim("userId", USER_ID.toString())
            .claim("role", "STUDENT")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
        .isEqualTo(USER_ID.toString());
  }
}
