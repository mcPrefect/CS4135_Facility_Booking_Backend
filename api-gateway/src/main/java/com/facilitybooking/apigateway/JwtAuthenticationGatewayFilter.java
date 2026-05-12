package com.facilitybooking.apigateway;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

/**
 * Validates the same HS256 JWTs issued by user-service and forwards identity to downstream
 * services (notably NLP expects {@code X-User-Id}).
 */
@Component
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {

    private final String jwtSecret;

    public JwtAuthenticationGatewayFilter(@Value("${jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }
        String path = request.getURI().getPath();
        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return unauthorized(exchange);
        }
        String token = auth.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            return unauthorized(exchange);
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.get("userId", String.class);
            if (userId == null || userId.isBlank()) {
                userId = claims.getSubject();
            }
            String role = null;
            Object roleClaim = claims.get("role");
            if (roleClaim != null) {
                role = roleClaim.toString();
            }

            ServerHttpRequest.Builder mutate = request.mutate();
            if (userId != null && !userId.isBlank()) {
                mutate.header("X-User-Id", userId);
            }
            if (role != null && !role.isBlank()) {
                mutate.header("X-User-Role", role);
            }
            return chain.filter(exchange.mutate().request(mutate.build()).build());
        } catch (JwtException | IllegalArgumentException ex) {
            return unauthorized(exchange);
        }
    }

    private static boolean isPublic(String path) {
        if (path.startsWith("/actuator")) {
            return true;
        }
        if (path.startsWith("/api/v1/auth/login") || path.startsWith("/api/v1/auth/register")) {
            return true;
        }
        if (path.equals("/api/nlp/health") || path.startsWith("/api/nlp/health")) {
            return true;
        }
        if (path.equals("/__gateway/not-found")) {
            return true;
        }
        return false;
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"message\":\"Authentication required\"}".getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
