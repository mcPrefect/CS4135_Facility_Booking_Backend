package com.facilitybooking.userservice.domain;

import lombok.Getter;

@Getter
public class User {

    //private Long id;
    private String email;
    private String password;
    private String role;

//    public User(Long id, String email, String password, String role) {
//        this.id = id;
//        this.email = email;
//        this.password = password;
//        this.role = role;
//    }

    public User(String email, String password, String role) {
        this.email = email;
        this.password = password;
        this.role = role;
    }

//    public String hashPassword(String password) {
//        //
//    }


    public void changePassword(String newPassword) {
        if (newPassword.equals(password)) {
            throw  new IllegalArgumentException("Please enter a different password");
        }
        this.password = password;
    }
}
