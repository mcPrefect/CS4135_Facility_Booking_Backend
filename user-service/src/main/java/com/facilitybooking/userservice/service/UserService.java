package com.facilitybooking.userservice.service;

import com.facilitybooking.userservice.domain.entity.User;
import com.facilitybooking.userservice.domain.valueobject.EmailAddress;
import com.facilitybooking.userservice.domain.valueobject.Role;
import com.facilitybooking.userservice.dto.LoginRequestDTO;
import com.facilitybooking.userservice.dto.RegisterRequestDTO;
import com.facilitybooking.userservice.dto.RegisterResponseDTO;
import com.facilitybooking.userservice.exception.InvalidCredentialsException;
import com.facilitybooking.userservice.mapper.UserMapper;
import com.facilitybooking.userservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final PasswordEncoder passwordEncoder;

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    public UserService(PasswordEncoder passwordEncoder, UserRepository userRepository, JwtService jwtService, UserMapper userMapper) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }
    //public User getUserById(@RequestBody User user) {}



    public User register(RegisterRequestDTO userDTO){

        if (userDTO.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password length must be at least 8 characters");
        }
        // hash the password
        String hashedPassword = passwordEncoder.encode(userDTO.getPassword());
        //User user = userMapper.toDomain(userDTO);
        // TODO: if user exist
        EmailAddress emailAddress = new EmailAddress(userDTO.getEmail());
        User userFromDb = userRepository.findByEmail(emailAddress);
        if (userFromDb != null){
            throw new RuntimeException("Email address already exists");
        }
        User user = new User(userDTO.getEmail(), hashedPassword, Role.STUDENT);

        return userRepository.save(user);
    }

    public String login(LoginRequestDTO userDTO){
        EmailAddress emailAddress = new EmailAddress(userDTO.getEmail());
        User userFromDb = userRepository.findByEmail(emailAddress);
        if (userFromDb == null || !passwordEncoder.matches(userDTO.getPassword(), userFromDb.getPasswordHashed())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        return jwtService.generateToken(userFromDb.getEmail(), userFromDb.getRole());

    }

    public long getUserCount(){
        return userRepository.count();
    }
}
