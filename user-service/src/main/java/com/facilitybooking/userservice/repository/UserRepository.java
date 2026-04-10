package com.facilitybooking.userservice.repository;

import com.facilitybooking.userservice.domain.User;

public interface UserRepository {
    User save(User user);

    User findByEmail(String email);
}
