package com.rentflow.listing.dto;

import com.rentflow.listing.entity.CancellationPolicy;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateListingRequest(
    @NotNull(message = "Vehicle ID is required")
    UUID vehicleId,

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    String title,

    String description,

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must not exceed 100 characters")
    String city,

    String address,

    BigDecimal latitude,

    BigDecimal longitude,

    @NotNull(message = "Base price per day is required")
    @DecimalMin(value = "0.01", message = "Base price must be greater than 0")
    BigDecimal basePricePerDay,

    @Size(min = 3, max = 3, message = "Currency must be 3 characters (e.g. VND, USD)")
    String currency,

    Integer dailyKmLimit,

    Boolean instantBook,

    CancellationPolicy cancellationPolicy
) {}
