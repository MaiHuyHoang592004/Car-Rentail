package com.rentflow.listing.dto;

import com.rentflow.listing.entity.Extra;
import com.rentflow.listing.entity.Listing;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleCategory;
import com.rentflow.vehicle.entity.VehicleStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ListingDetailResponse(
    UUID id,
    String title,
    String description,
    String city,
    String address,
    BigDecimal basePricePerDay,
    String currency,
    Integer dailyKmLimit,
    Boolean instantBook,
    com.rentflow.listing.entity.CancellationPolicy cancellationPolicy,
    List<String> photos,
    VehicleSummary vehicleSummary,
    List<ExtraResponse> extras
) {
    public record VehicleSummary(
        VehicleCategory category,
        String make,
        String model,
        Integer year,
        com.rentflow.vehicle.entity.TransmissionType transmission,
        com.rentflow.vehicle.entity.FuelType fuelType,
        Integer seats,
        VehicleStatus status
    ) {}

    public static ListingDetailResponse from(Listing listing, Vehicle vehicle, List<Extra> extras) {
        return from(listing, vehicle, extras, List.of());
    }

    public static ListingDetailResponse from(
            Listing listing,
            Vehicle vehicle,
            List<Extra> extras,
            List<String> photos) {
        VehicleSummary vs = vehicle != null
            ? new VehicleSummary(
                vehicle.getCategory(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getManufactureYear(),
                vehicle.getTransmission(),
                vehicle.getFuelType(),
                vehicle.getSeats(),
                vehicle.getStatus())
            : null;

        List<ExtraResponse> extraResponses = extras.stream()
            .filter(extra -> Boolean.TRUE.equals(extra.getActive()))
            .map(ExtraResponse::from)
            .toList();

        return new ListingDetailResponse(
            listing.getId(),
            listing.getTitle(),
            listing.getDescription(),
            listing.getCity(),
            listing.getAddress(),
            listing.getBasePricePerDay(),
            listing.getCurrency(),
            listing.getDailyKmLimit(),
            listing.getInstantBook(),
            listing.getCancellationPolicy(),
            photos,
            vs,
            extraResponses
        );
    }
}
