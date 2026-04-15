package com.facilitybooking.userservice.mapper;

import com.facilitybooking.userservice.domain.entity.User;
import com.facilitybooking.userservice.messaging.UserRegisteredEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class UserEventMapper {

    public UserRegisteredEvent toEvent(User user) {
        return new UserRegisteredEvent(
                "UserRegistered",
                user.getId().toString(),
                user.getEmail(),
                user.getRole().name(),
                Instant.now().toString()
        );
    }
}
