package com.facilitybooking.approvalservice.repository;

import com.facilitybooking.approvalservice.domain.ApprovalStatus;
import com.facilitybooking.approvalservice.domain.ApprovalTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, UUID> {

    Optional<ApprovalTask> findByBookingId(UUID bookingId);

    boolean existsByBookingId(UUID bookingId);

    List<ApprovalTask> findByStatusOrderByCreatedAtAsc(ApprovalStatus status);
}
