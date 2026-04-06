package com.plassey.notification.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "log_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LogEntry {

    @Id
    private UUID logEntryId;

    @Column(nullable = false)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogLevel level;

    @Column(nullable = false, length = 5000)
    private String message;

    private String correlationId;

    @Column(nullable = false)
    private Instant timestamp;

    public static LogEntry create(String serviceName, LogLevel level, String message, String correlationId) {
        if (serviceName == null || serviceName.isBlank()) throw new IllegalArgumentException("INV-L1: serviceName must not be blank");
        if (level == null)    throw new IllegalArgumentException("INV-L2: level must not be null");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("INV-L3: message must not be blank");

        LogEntry e = new LogEntry();
        e.logEntryId    = UUID.randomUUID();
        e.serviceName   = serviceName;
        e.level         = level;
        e.message       = message;
        e.correlationId = correlationId;
        e.timestamp     = Instant.now();
        return e;
    }
}
