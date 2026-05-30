package com.rentflow.booking.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;

import java.util.UUID;

public record RequestedExtra(
        @NotNull(message = "extraId is required")
        UUID extraId,
        @Min(value = 1, message = "quantity must be greater than zero")
        @Max(value = 5, message = "quantity must be at most 5")
        int quantity) {
}
