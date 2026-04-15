package com.facilitybooking.userservice.domain.entity;

import com.facilitybooking.userservice.domain.valueobject.Role;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.UUID;

@Getter
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHashed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;


    public User(String email, String passwordHashed, Role role) {
        this.email = email;
        this.passwordHashed = passwordHashed;
        this.role = role;
    }



    public User() {

    }


    public void changePassword(String newHashedPassword) {
        if (newHashedPassword.equals(this.passwordHashed)) {
            throw new IllegalArgumentException("Please enter a different password");
        }
        this.passwordHashed = newHashedPassword;
    }


}
