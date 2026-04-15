package com.facilitybooking.userservice.domain.valueobject;

import java.util.regex.Pattern;

public class EmailAddress {
    private final String value;

    private static final Pattern regex = Pattern.compile("^\\S+@\\S+\\.\\S+$");


    public EmailAddress(String value) {
        if (!regex.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Email address.");
        }
        this.value = value;
    }


    public String getValue() {
        return  value;
    }
}
