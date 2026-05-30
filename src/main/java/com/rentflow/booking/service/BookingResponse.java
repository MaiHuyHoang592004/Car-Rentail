package com.rentflow.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rentflow.booking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        BookingStatus status,
        UUID listingId,
        String listingTitle,
        UUID customerId,
        UUID hostId,
        LocalDate pickupDate,
        LocalDate returnDate,
        String pickupLocation,
        String returnLocation,
        Instant holdExpiresAt,
        Instant hostApprovalExpiresAt,
        BigDecimal totalAmount,
        String currency,
        JsonNode priceSnapshot,
        JsonNode policySnapshot,
        String rejectionReason,
        Instant createdAt,
        boolean voidRetryRequired,
        String paymentRetryState) {

    public BookingResponse(
            UUID id,
            BookingStatus status,
            UUID listingId,
            String listingTitle,
            UUID customerId,
            UUID hostId,
            LocalDate pickupDate,
            LocalDate returnDate,
            String pickupLocation,
            String returnLocation,
            Instant holdExpiresAt,
            Instant hostApprovalExpiresAt,
            BigDecimal totalAmount,
            String currency,
            JsonNode priceSnapshot,
            JsonNode policySnapshot,
            String rejectionReason,
            Instant createdAt) {
        this(
                id,
                status,
                listingId,
                listingTitle,
                customerId,
                hostId,
                pickupDate,
                returnDate,
                pickupLocation,
                returnLocation,
                holdExpiresAt,
                hostApprovalExpiresAt,
                totalAmount,
                currency,
                priceSnapshot,
                policySnapshot,
                rejectionReason,
                createdAt,
                false,
                null);
    }
}
