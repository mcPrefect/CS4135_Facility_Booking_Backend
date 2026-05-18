package com.facilitybooking.userservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginResponseDTO {
    private String userId;
    private String email;
    private String message;
    private String token;
}
