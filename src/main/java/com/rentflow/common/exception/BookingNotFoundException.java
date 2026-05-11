package com.rentflow.common.exception;

public class BookingNotFoundException extends RentFlowException {

    public BookingNotFoundException(String bookingId) {
        super("BOOKING_NOT_FOUND", "Booking not found: " + bookingId);
    }
}
