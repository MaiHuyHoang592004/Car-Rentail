package com.rentflow.listing.dto;

import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ListingSummaryResponse(
    UUID id,
    String title,
    String city,
    ListingStatus status,
    BigDecimal basePricePerDay,
    String currency,
    Instant createdAt
) {
    public static ListingSummaryResponse from(Listing listing) {
        return new ListingSummaryResponse(
            listing.getId(),
            listing.getTitle(),
            listing.getCity(),
            listing.getStatus(),
            listing.getBasePricePerDay(),
            listing.getCurrency(),
            listing.getCreatedAt()
        );
    }
}
