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
    public ResponseEntity<LogEntry> ingestLog(@RequestBody Map<String, String> body) {
        LogLevel level = LogLevel.valueOf(body.getOrDefault("level", "INFO"));
        LogEntry entry = logEntryService.record(
                body.get("serviceName"),
                level,
                body.get("message"),
                body.get("correlationId"));
        return ResponseEntity.ok(entry);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<LogEntry>> getLogs(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        LogLevel logLevel = level != null ? LogLevel.valueOf(level) : null;
        return ResponseEntity.ok(logEntryService.query(serviceName, logLevel, PageRequest.of(page, size)));
    }
}
