package com.facilitybooking.approvalservice.application;

import com.facilitybooking.approvalservice.domain.ApprovalDomainEvent;
import com.facilitybooking.approvalservice.domain.ApprovalStatus;
import com.facilitybooking.approvalservice.domain.ApprovalTask;
import com.facilitybooking.approvalservice.infrastructure.messaging.ApprovalEventPublisher;
import com.facilitybooking.approvalservice.repository.ApprovalTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalTaskRepository taskRepository;
    private final ApprovalEventPublisher  eventPublisher;

    public ApprovalService(ApprovalTaskRepository taskRepository,
                           ApprovalEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates an approval task when a BookingCreated event is received.
     * Idempotent: if a task already exists for this bookingId, it is silently skipped.
     */
    @Transactional
    public void createTask(UUID bookingId, UUID userId, String facilityName,
                           Instant bookingStart, Instant bookingEnd) {
        if (taskRepository.existsByBookingId(bookingId)) {
            log.info("Idempotency: ApprovalTask already exists for bookingId={} – skipping.", bookingId);
            return;
        }
        ApprovalTask task = ApprovalTask.create(bookingId, userId, facilityName, bookingStart, bookingEnd);
        taskRepository.save(task);
        log.info("ApprovalTask created: taskId={} bookingId={}", task.getTaskId(), bookingId);
    }

    /**
     * Admin approves a pending booking.
     * Publishes BookingApproved event to RabbitMQ.
     */
    @Transactional
    public ApprovalTask approve(UUID bookingId, UUID adminId, String reason) {
        ApprovalTask task = findByBookingIdOrThrow(bookingId);
        ApprovalDomainEvent event = task.approve(adminId, reason);
        taskRepository.save(task);
        eventPublisher.publish(event);
        log.info("Booking approved: bookingId={} by adminId={}", bookingId, adminId);
        return task;
    }

    /**
     * Admin rejects a pending booking.
     * Publishes BookingRejected event to RabbitMQ.
     */
    @Transactional
    public ApprovalTask reject(UUID bookingId, UUID adminId, String reason) {
        ApprovalTask task = findByBookingIdOrThrow(bookingId);
        ApprovalDomainEvent event = task.reject(adminId, reason);
        taskRepository.save(task);
        eventPublisher.publish(event);
        log.info("Booking rejected: bookingId={} by adminId={}", bookingId, adminId);
        return task;
    }

    @Transactional(readOnly = true)
    public List<ApprovalTask> getPendingTasks() {
        return taskRepository.findByStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public ApprovalTask getByBookingId(UUID bookingId) {
        return findByBookingIdOrThrow(bookingId);
    }

    private ApprovalTask findByBookingIdOrThrow(UUID bookingId) {
        return taskRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ApprovalTaskNotFoundException(
                        "No approval task found for bookingId: " + bookingId));
    }
}
