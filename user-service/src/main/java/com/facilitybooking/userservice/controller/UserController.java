package com.facilitybooking.userservice.controller;

import com.facilitybooking.userservice.domain.entity.User;
import com.facilitybooking.userservice.dto.*;
//import com.facilitybooking.userservice.dto.UserResponseDTO;
import com.facilitybooking.userservice.exception.InvalidCredentialsException;
import com.facilitybooking.userservice.messaging.UserEventPublisher;
import com.facilitybooking.userservice.service.UserService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/auth")
public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private UserEventPublisher userEventPublisher;

    @ApiResponse(responseCode = "200", description = "User registered successfully")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> register(@RequestBody RegisterRequestDTO userDTO) {
        try {
            User user = userService.register(userDTO);
            userEventPublisher.publishUserRegistered(user);

            RegisterResponseDTO userResponseDTO = new RegisterResponseDTO();
            userResponseDTO.setEmail(user.getEmail());
            userResponseDTO.setUserId(user.getId());
            userResponseDTO.setMessage("User registered successfully");
            return ResponseEntity.ok(userResponseDTO);
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(registerError(e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(registerError(e.getMessage()));
        }
    }


    @ApiResponse(responseCode = "200", description = "User logged in successfully")
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO userDTO) {
        try {
            String token = userService.login(userDTO);
            LoginResponseDTO loginResponseDTO = new LoginResponseDTO();
            loginResponseDTO.setToken(token);
            return ResponseEntity.ok(loginResponseDTO);
        } catch (InvalidCredentialsException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(loginError(e.getMessage()));
        }
    }



    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("student/test")
    public ResponseEntity<String> studentTest() {
        return ResponseEntity.ok("student endpoint hit");
    }

    @PreAuthorize("hasRole('STAFF')")
    @GetMapping("staff/test")
    public ResponseEntity<String> stuffTest() {
        return ResponseEntity.ok("staff endpoint hit");
    }

    private RegisterResponseDTO registerError(String message) {
        RegisterResponseDTO dto = new RegisterResponseDTO();
        dto.setMessage(message);
        return dto;
    }

    private LoginResponseDTO loginError(String message) {
        LoginResponseDTO dto = new LoginResponseDTO();
        dto.setMessage(message);
        return dto;
    }
}
