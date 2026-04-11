package com.facilitybooking.bookingservice.application;

public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) { super(message); }
}
