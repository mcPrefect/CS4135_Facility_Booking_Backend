package com.facilitybooking.userservice.service;


import com.facilitybooking.userservice.domain.User;
import com.facilitybooking.userservice.dto.LoginRequestDTO;
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



    public User register(LoginRequestDTO userDTO){
        // TODO query user by username cause username is unique
        // TODO hash password
        // suppose just insert name and password now
        // hash the password
        String hashedPassword = passwordEncoder.encode(userDTO.getPassword());
        //User user = userMapper.toDomain(userDTO);
        User user = new User(userDTO.getEmail(), hashedPassword);

        return userRepository.save(user);
    }

    public String login(LoginRequestDTO userDTO){
        // get user by username
        User userFromDb = userRepository.findByEmail(userDTO.getEmail());
        if (userFromDb == null) {
            throw new RuntimeException("User not found");
        }
        // compare the hashed password with the password from db
        if (!passwordEncoder.matches(userDTO.getPassword(), userFromDb.getPassword())) {
            throw new RuntimeException("Wrong password");
        }
        return jwtService.generateToken(userFromDb.getEmail(), userDTO.getRole());



    }
}
