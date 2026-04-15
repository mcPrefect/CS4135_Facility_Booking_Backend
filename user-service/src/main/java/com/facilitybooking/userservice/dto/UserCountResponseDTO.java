package com.facilitybooking.userservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserCountResponseDTO {
    long count;

    public UserCountResponseDTO(long count) {
        this.count = count;
    }

}
