package com.facilitybooking.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Enables @PreAuthorize / @PostAuthorize on controllers.
 * JWT validation is handled by the API Gateway; this service
 * trusts the gateway and builds a security principal from the
 * X-User-Id and X-User-Role headers via GatewayTrustFilter.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final GatewayTrustFilter gatewayTrustFilter;

    public SecurityConfig(GatewayTrustFilter gatewayTrustFilter) {
        this.gatewayTrustFilter = gatewayTrustFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, GatewayTrustFilter gatewayTrustFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(gatewayTrustFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(gatewayTrustFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
