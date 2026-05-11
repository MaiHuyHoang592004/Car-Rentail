package com.rentflow.listing.dto;

import com.rentflow.listing.entity.CancellationPolicy;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpdateListingRequest(
    @Size(max = 200, message = "Title must not exceed 200 characters")
    String title,

    String description,

    @Size(max = 100, message = "City must not exceed 100 characters")
    String city,

    String address,

    BigDecimal latitude,

    BigDecimal longitude,

    @DecimalMin(value = "0.01", message = "Base price must be greater than 0")
    BigDecimal basePricePerDay,

    Integer dailyKmLimit,

    Boolean instantBook,

    CancellationPolicy cancellationPolicy
) {}
