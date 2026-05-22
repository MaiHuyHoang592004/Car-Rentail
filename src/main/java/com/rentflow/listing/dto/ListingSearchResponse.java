package com.rentflow.listing.dto;

import com.rentflow.vehicle.entity.FuelType;
import com.rentflow.vehicle.entity.TransmissionType;
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
}
