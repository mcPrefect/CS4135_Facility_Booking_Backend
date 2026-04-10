package com.facilitybooking.userservice.controller;

import com.facilitybooking.userservice.domain.User;
import com.facilitybooking.userservice.dto.LoginRequestDTO;
import com.facilitybooking.userservice.dto.LoginResponseDTO;
import com.facilitybooking.userservice.dto.UserResponseDTO;
import com.facilitybooking.userservice.service.UserService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @ApiResponse(responseCode = "200", description = "User registered successfully")
    @PostMapping("/user/register")
    public ResponseEntity<UserResponseDTO> register(@RequestBody LoginRequestDTO userDTO) {
        User user = userService.register(userDTO);
        UserResponseDTO userResponseDTO = new UserResponseDTO();
        userResponseDTO.setEmail(user.getEmail());
        return ResponseEntity.ok(userResponseDTO);
    }


    @ApiResponse(responseCode = "200", description = "User logged in successfully")
    @PostMapping("/user/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO userDTO) {
        System.out.println("login endpoint hit");
        String token = userService.login(userDTO);
        LoginResponseDTO loginResponseDTO = new LoginResponseDTO();
        loginResponseDTO.setToken(token);
        return ResponseEntity.ok(loginResponseDTO);
    }
}
