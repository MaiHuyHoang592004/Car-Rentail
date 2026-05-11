package com.rentflow.listing.dto;

import com.rentflow.listing.entity.Listing;
import com.rentflow.vehicle.entity.FuelType;
import com.rentflow.vehicle.entity.TransmissionType;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleCategory;

import java.math.BigDecimal;
import java.util.UUID;

public record ListingSearchResponse(
    UUID id,
    String title,
    String city,
    VehicleCategory category,
    BigDecimal basePricePerDay,
    String currency,
    Integer seats,
    TransmissionType transmission,
    FuelType fuelType,
    String coverPhotoUrl,
    BigDecimal ratingAverage
) {
    public static ListingSearchResponse from(Listing listing) {
        Vehicle v = listing.getVehicle();
        return new ListingSearchResponse(
            listing.getId(),
            listing.getTitle(),
            listing.getCity(),
            v != null ? v.getCategory() : null,
            listing.getBasePricePerDay(),
            listing.getCurrency(),
            v != null ? v.getSeats() : null,
            v != null ? v.getTransmission() : null,
            v != null ? v.getFuelType() : null,
            null,
            null
        );
    }
}
