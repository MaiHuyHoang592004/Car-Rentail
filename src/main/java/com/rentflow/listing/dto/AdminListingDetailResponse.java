package com.rentflow.listing.dto;

import java.util.UUID;
import java.time.Instant;
import com.rentflow.vehicle.entity.VehicleStatus;

public record AdminListingDetailResponse(
    ListingResponse listing,
    HostSummary host,
    VehicleRiskSummary vehicle,
    BookingSummary bookingSummary,
    ModerationSummary moderation
) {
    public record HostSummary(
        UUID id,
        String fullName,
        String email,
        int activeListings
    ) {}

    public record VehicleRiskSummary(
        UUID id,
        VehicleStatus status,
        int activeListings
    ) {}

    public record BookingSummary(
        int activeBookings
    ) {}

    public record ModerationSummary(
        String suspensionReason,
        String suspensionSource,
        Instant suspensionUntil,
        String rejectedReason,
        Instant rejectedAt
    ) {}
}
