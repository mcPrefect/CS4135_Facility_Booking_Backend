package com.facilitybooking.userservice.repository;
import com.facilitybooking.userservice.domain.entity.User;
import com.facilitybooking.userservice.domain.valueobject.EmailAddress;
import com.facilitybooking.userservice.mapper.UserMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final JpaUserRepository jpaUserResitory;
    private final UserMapper userMapper;

    public UserRepositoryImpl(JpaUserRepository jpaUserResitory, UserMapper userMapper) {
        this.jpaUserResitory = jpaUserResitory;
        this.userMapper = userMapper;
    }


    @Override
    public User save(User user) {
        return jpaUserResitory.save(user);
    }



    @Override
    public User findByEmail(EmailAddress email) {
        return jpaUserResitory.findByEmail(email.getValue());
    }

    @Override
    public long count() {
        return jpaUserResitory.count();
    }

}
