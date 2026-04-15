package com.facilitybooking.userservice.repository;
import com.facilitybooking.userservice.domain.entity.User;
import com.facilitybooking.userservice.domain.valueobject.EmailAddress;

public interface UserRepository {
    User save(User user);

    User findByEmail(EmailAddress email);

    long count();
}
