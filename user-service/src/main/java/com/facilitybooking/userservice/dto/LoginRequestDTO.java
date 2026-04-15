package com.facilitybooking.userservice.dto;

import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDTO {
    @NonNull
    @Email
    private String email;

    @NonNull
    private String password;
}
