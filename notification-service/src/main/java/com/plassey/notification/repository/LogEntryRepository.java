package com.plassey.notification.repository;

import com.plassey.notification.domain.LogEntry;
import com.plassey.notification.domain.LogLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LogEntryRepository extends JpaRepository<LogEntry, UUID> {
    List<LogEntry> findByCorrelationId(String correlationId);
    Page<LogEntry> findByServiceNameAndLevel(String serviceName, LogLevel level, Pageable pageable);
    Page<LogEntry> findByServiceName(String serviceName, Pageable pageable);
}
