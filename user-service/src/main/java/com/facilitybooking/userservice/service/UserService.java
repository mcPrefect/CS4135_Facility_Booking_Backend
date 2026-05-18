package com.facilitybooking.userservice.service;

import com.facilitybooking.userservice.domain.entity.User;
import com.facilitybooking.userservice.domain.valueobject.EmailAddress;
import com.facilitybooking.userservice.domain.valueobject.Role;
import com.facilitybooking.userservice.dto.LoginRequestDTO;
import com.facilitybooking.userservice.dto.LoginResponseDTO;
import com.facilitybooking.userservice.dto.RegisterRequestDTO;
import com.facilitybooking.userservice.exception.InvalidCredentialsException;
import com.facilitybooking.userservice.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final PasswordEncoder passwordEncoder;

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public UserService(PasswordEncoder passwordEncoder, UserRepository userRepository, JwtService jwtService) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }



    public User register(RegisterRequestDTO userDTO){

        if (userDTO.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password length must be at least 8 characters");
        }
        // hash the password
        String hashedPassword = passwordEncoder.encode(userDTO.getPassword());
        EmailAddress emailAddress = new EmailAddress(userDTO.getEmail());
        User userFromDb = userRepository.findByEmail(emailAddress);
        if (userFromDb != null){
            throw new RuntimeException("Email address already exists");
        }
        User user = new User(userDTO.getEmail(), hashedPassword, Role.STUDENT);

        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw  new RuntimeException("Email address already exists");
        }
    }

    public LoginResponseDTO login(LoginRequestDTO userDTO){
        EmailAddress emailAddress = new EmailAddress(userDTO.getEmail());
        User userFromDb = userRepository.findByEmail(emailAddress);
        if (userFromDb == null || !passwordEncoder.matches(userDTO.getPassword(), userFromDb.getPasswordHashed())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(jwtService.generateToken(userFromDb.getEmail(), userFromDb.getRole(), userFromDb.getId()));
        response.setEmail(userFromDb.getEmail());
        response.setUserId(userFromDb.getId().toString());
        return response;
    }

    public long getUserCount(){
        return userRepository.count();
    }
}
