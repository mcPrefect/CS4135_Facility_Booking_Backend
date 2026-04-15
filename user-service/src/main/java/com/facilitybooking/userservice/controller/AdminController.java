package com.facilitybooking.userservice.controller;

import com.facilitybooking.userservice.dto.UserCountResponseDTO;
import com.facilitybooking.userservice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @Autowired
    private UserService userService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users/count")
    public ResponseEntity<UserCountResponseDTO> getUserCount() {
        long count = userService.getUserCount();
        return ResponseEntity.ok(new UserCountResponseDTO(count));
    }
}
