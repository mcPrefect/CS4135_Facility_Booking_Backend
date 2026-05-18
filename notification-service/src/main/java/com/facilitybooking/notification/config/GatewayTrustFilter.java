package com.facilitybooking.notification.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Trusts the API Gateway's identity headers (X-User-Id, X-User-Role) and
 * builds a Spring Security principal from them, so @PreAuthorize expressions
 * work without re-validating the JWT in this service.
 *
 * The gateway validates the JWT and forwards these headers on every
 * authenticated request; this filter must run before Spring Security's
 * authorization checks.
 */
@Component
public class GatewayTrustFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER  = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader(USER_ID_HEADER);
        String role   = request.getHeader(USER_ROLE_HEADER);

        if (userId != null && role != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Role values from the gateway are plain strings (e.g. "ADMIN").
            // Spring Security's hasRole() checks for the "ROLE_" prefix.
            var authority = new SimpleGrantedAuthority("ROLE_" + role);
            var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of(authority));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
