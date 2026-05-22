package com.rentflow.common.exception;

public class BookingNotFoundException extends ResourceNotFoundException {

    public BookingNotFoundException(String bookingId) {
        super("BOOKING_NOT_FOUND", "Booking", bookingId);
    }
}
