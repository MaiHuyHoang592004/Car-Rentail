package com.rentflow.booking.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateBookingRequest(
        UUID listingId,
        LocalDate pickupDate,
        LocalDate returnDate,
        String pickupLocation,
        String returnLocation,
        List<RequestedExtra> extras) {
}
