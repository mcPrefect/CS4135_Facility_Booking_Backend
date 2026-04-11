package com.facilitybooking.bookingservice.application;

public class BookingAccessDeniedException extends RuntimeException {
    public BookingAccessDeniedException(String message) { super(message); }
}
