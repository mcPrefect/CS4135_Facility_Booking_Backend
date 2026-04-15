package com.facilitybooking.userservice.config;

import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Random;

public class KeyGenerator {
    public static void main(String[] args) {

        byte[] keyBytes = new byte[32];
        new Random().nextBytes(keyBytes);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        String base64Key = Encoders.BASE64.encode(key.getEncoded());
        System.out.println(base64Key);
    }
}
