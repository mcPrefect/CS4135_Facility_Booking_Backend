package com.facilitybooking.notification.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    private UUID notificationId;

    @Column(nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    private boolean isRead;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant sentAt;
    private String failureReason;

    // --- Invariant-enforcing factory method ---
    public static Notification create(UUID recipientId, NotificationType type, Channel channel, String message) {
        if (recipientId == null) throw new IllegalArgumentException("INV-N1: recipientId must not be null");
        if (type == null)        throw new IllegalArgumentException("INV-N2: type must not be null");
        if (channel == null)     throw new IllegalArgumentException("INV-N4: channel must not be null");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("INV-N5: message must not be blank");

        Notification n = new Notification();
        n.notificationId = UUID.randomUUID();
        n.recipientId    = recipientId;
        n.type           = type;
        n.channel        = channel;
        n.message        = message;
        n.status         = NotificationStatus.PENDING;
        n.isRead         = false;
        n.createdAt      = Instant.now();
        return n;
    }

    public void markSent() {
        assertTransitionable("markSent");
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void markFailed(String reason) {
        assertTransitionable("markFailed");
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason;
    }

    public void markRead() {
        this.isRead = true;
    }

    private void assertTransitionable(String op) {
        if (this.status != NotificationStatus.PENDING) {
            throw new IllegalStateException("INV-N3: Cannot call " + op + " on notification in state " + this.status);
        }
    }
}
