package com.facilitybooking.userservice.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class LoginResponseDTO {
    private UUID userId;
    private String email;
    private String message;
    private String token;
}
