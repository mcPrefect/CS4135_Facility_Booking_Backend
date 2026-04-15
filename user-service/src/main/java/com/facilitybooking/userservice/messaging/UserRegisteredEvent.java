package com.facilitybooking.userservice.messaging;

import com.facilitybooking.userservice.domain.valueobject.Role;

import java.time.LocalDateTime;

public class UserRegisteredEvent {
    private String eventType;
    private String userId;
    private String email;
    private String Role;
    private String occurredAt;
    public UserRegisteredEvent(String eventType, String userId, String email, String role, String occurredAt) {
        this.eventType = eventType;
        this.userId = userId;
        this.email = email;
        this.Role = role;
        this.occurredAt = occurredAt;
    }



}
