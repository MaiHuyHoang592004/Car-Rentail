package com.rentflow.booking.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull(message = "listingId is required")
        UUID listingId,
        @NotNull(message = "pickupDate is required")
        LocalDate pickupDate,
        @NotNull(message = "returnDate is required")
        LocalDate returnDate,
        String pickupLocation,
        String returnLocation,
        @NotNull(message = "extras is required")
        @Valid
        List<RequestedExtra> extras) {
}
