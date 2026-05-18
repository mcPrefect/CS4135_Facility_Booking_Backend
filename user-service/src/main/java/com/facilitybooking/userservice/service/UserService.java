package com.facilitybooking.userservice.service;

import com.facilitybooking.userservice.domain.entity.User;
import com.facilitybooking.userservice.domain.valueobject.EmailAddress;
import com.facilitybooking.userservice.domain.valueobject.Role;
import com.facilitybooking.userservice.dto.LoginRequestDTO;
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
        Role role = Role.STUDENT;
        try {
            if (userDTO.getRole() != null) role = Role.valueOf(userDTO.getRole().toUpperCase());
        } catch (IllegalArgumentException ignored) {}
        User user = new User(userDTO.getEmail(), hashedPassword, role);

        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw  new RuntimeException("Email address already exists");
        }
    }

    public User login(LoginRequestDTO userDTO){
        EmailAddress emailAddress = new EmailAddress(userDTO.getEmail());
        User userFromDb = userRepository.findByEmail(emailAddress);
        if (userFromDb == null || !passwordEncoder.matches(userDTO.getPassword(), userFromDb.getPasswordHashed())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        return userFromDb;
    }

    public long getUserCount(){
        return userRepository.count();
    }
}
