package com.rentflow.listing.dto;

import com.rentflow.listing.entity.PricingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateExtraRequest(
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        PricingType pricingType,

        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        BigDecimal price,

        Boolean active
) {
}
