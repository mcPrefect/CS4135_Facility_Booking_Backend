package com.plassey.notification.application;

import com.plassey.notification.domain.LogEntry;
import com.plassey.notification.domain.LogLevel;
import com.plassey.notification.repository.LogEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogEntryService {

    private final LogEntryRepository logEntryRepository;

    public LogEntryService(LogEntryRepository repo) {
        this.logEntryRepository = repo;
    }

    @Transactional
    public LogEntry record(String serviceName, LogLevel level, String message, String correlationId) {
        LogEntry entry = LogEntry.create(serviceName, level, message, correlationId);
        return logEntryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<LogEntry> query(String serviceName, LogLevel level, Pageable pageable) {
        if (serviceName != null && level != null) {
            return logEntryRepository.findByServiceNameAndLevel(serviceName, level, pageable);
        } else if (serviceName != null) {
            return logEntryRepository.findByServiceName(serviceName, pageable);
        }
        return logEntryRepository.findAll(pageable);
    }
}
