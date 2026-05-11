package com.rentflow.listing.dto;

import com.rentflow.listing.entity.Extra;
import com.rentflow.listing.entity.PricingType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExtraResponse(
    UUID id,
    String name,
    PricingType pricingType,
    BigDecimal price,
    Boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static ExtraResponse from(Extra extra) {
        return new ExtraResponse(
            extra.getId(),
            extra.getName(),
            extra.getPricingType(),
            extra.getPrice(),
            extra.getActive(),
            extra.getCreatedAt(),
            extra.getUpdatedAt()
        );
    }
}
