package com.facilitybooking.apigateway;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.server.reactive.ServerHttpRequest;

@RestController
public class GatewayErrorController {

    @RequestMapping("/__gateway/not-found")
    public ResponseEntity<Map<String, Object>> apiNotFound(ServerHttpRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", "Not Found");
        body.put("message", "No API route matches this path");
        body.put("path", request.getPath().value());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
