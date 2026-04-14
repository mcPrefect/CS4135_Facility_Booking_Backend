package com.facilitybooking.notification.infrastructure.web;

import com.facilitybooking.notification.application.LogEntryService;
import com.facilitybooking.notification.domain.LogEntry;
import com.facilitybooking.notification.domain.LogLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/logs")
public class LogController {

    private final LogEntryService logEntryService;

    public LogController(LogEntryService svc) {
        this.logEntryService = svc;
    }

    @PostMapping
    public ResponseEntity<?> ingestLog(@RequestBody Map<String, String> body) {
        LogLevel level;
        try {
            level = LogLevel.valueOf(body.getOrDefault("level", "INFO").toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid log level. Valid values: INFO, WARN, ERROR"));
        }

        if (body.get("serviceName") == null || body.get("message") == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "serviceName and message are required fields"));
        }

        LogEntry entry = logEntryService.record(
                body.get("serviceName"),
                level,
                body.get("message"),
                body.get("correlationId"));
        return ResponseEntity.ok(entry);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getLogs(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        LogLevel logLevel = null;
        if (level != null) {
            try {
                logLevel = LogLevel.valueOf(level.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid log level. Valid values: INFO, WARN, ERROR"));
            }
        }

        Page<LogEntry> results = logEntryService.query(serviceName, logLevel, PageRequest.of(page, size));
        return ResponseEntity.ok(results);
    }
}
