package com.rentflow.vehicle.dto;

import com.rentflow.listing.entity.ListingStatus;

import java.util.List;
import java.util.UUID;

public record VehicleArchivePreviewResponse(
        UUID vehicleId,
        List<AffectedListing> affectedListings,
        boolean hasActiveBookings,
        boolean archiveAllowed,
        String blockingReason
) {
    public record AffectedListing(
            UUID id,
            String title,
            ListingStatus status
    ) {
    }
}
