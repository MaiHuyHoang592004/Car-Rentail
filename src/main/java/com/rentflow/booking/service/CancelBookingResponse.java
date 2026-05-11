package com.rentflow.booking.service;

import com.rentflow.booking.entity.BookingStatus;

import java.util.UUID;

public record CancelBookingResponse(
        UUID id,
        BookingStatus status,
        String cancellationReason) {
}
