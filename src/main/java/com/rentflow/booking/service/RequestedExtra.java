package com.rentflow.booking.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RequestedExtra(
        @NotNull(message = "extraId is required")
        UUID extraId,
        @Min(value = 1, message = "quantity must be greater than zero")
        int quantity) {
}
