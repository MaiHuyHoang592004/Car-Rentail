package com.rentflow.listing.dto;

import java.util.UUID;

public record AdminListingDetailResponse(
    ListingResponse listing,
    HostSummary host,
    BookingSummary bookingSummary
) {
    public record HostSummary(
        UUID id,
        String fullName,
        String email
    ) {}

    public record BookingSummary(
        int activeBookings
    ) {}
}
