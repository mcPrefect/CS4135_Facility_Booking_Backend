package com.facilitybooking.userservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDTO {
    //private Long id;
    private String email;
    private String password;
    private String role;
}
