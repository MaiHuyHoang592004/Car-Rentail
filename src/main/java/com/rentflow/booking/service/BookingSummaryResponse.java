package com.rentflow.booking.service;

import com.rentflow.booking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BookingSummaryResponse(
        UUID id,
        BookingStatus status,
        UUID listingId,
        String listingTitle,
        LocalDate pickupDate,
        LocalDate returnDate,
        Instant holdExpiresAt,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt) {
}
