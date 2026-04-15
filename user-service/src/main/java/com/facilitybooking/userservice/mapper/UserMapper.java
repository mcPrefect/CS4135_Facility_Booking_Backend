package com.facilitybooking.userservice.mapper;
import com.facilitybooking.userservice.domain.entity.User;
import com.facilitybooking.userservice.dto.LoginRequestDTO;
import org.springframework.stereotype.Component;

//import com.facilitybooking.userservice.entity.UserEntity;
@Component
public class UserMapper {
    //
//    public UserEntity toEntity(User user) {
//        UserEntity userEntity = new UserEntity();
//        userEntity.setEmail(user.getEmail());
//        userEntity.setPassword(user.getPassword());
//        return userEntity;
//    }

    // in service layer, convert UserDTO to domain User
//    public User toDomain(LoginRequestDTO userDTO) {
//        return new User(userDTO.getId(), userDTO.getEmail(), userDTO.getPassword(), userDTO.getRole());
//    }

    // in repository layer, convert UserEntity to domain User
//    public User toDomain(UserEntity userEntity) {
//        return new User(userEntity.getId(), userEntity.getEmail(), userEntity.getPassword());
//    }


}
