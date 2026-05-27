package com.rentflow.booking.service;

import com.rentflow.booking.entity.BookingStatus;

import java.util.UUID;

public record CancelBookingResponse(
        UUID id,
        BookingStatus status,
        String cancellationReason,
        boolean cancellationCompleted,
        boolean voidRetryRequired,
        String code,
        String paymentRetryState) {

    public CancelBookingResponse(UUID id, BookingStatus status, String cancellationReason) {
        this(id, status, cancellationReason, true, false, null, null);
    }
}
