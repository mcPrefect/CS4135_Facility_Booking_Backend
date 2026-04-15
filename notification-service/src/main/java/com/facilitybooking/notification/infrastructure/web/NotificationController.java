package com.facilitybooking.notification.infrastructure.web;

import com.facilitybooking.notification.application.NotificationService;
import com.facilitybooking.notification.domain.Notification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService svc) {
        this.notificationService = svc;
    }

    @GetMapping("/{userId}")
    @PreAuthorize("authentication.name == #userId.toString() or hasRole('ADMIN')")
    public ResponseEntity<List<Notification>> getNotifications(@PathVariable UUID userId) {
        return ResponseEntity.ok(notificationService.getNotificationsForUser(userId));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID notificationId) {
        notificationService.markRead(notificationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/unread-count")
    @PreAuthorize("authentication.name == #userId.toString() or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> unreadCount(@PathVariable UUID userId) {
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.getUnreadCount(userId)));
    }
}
