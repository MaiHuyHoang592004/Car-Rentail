package com.rentflow.listing.dto;

import com.rentflow.listing.entity.CancellationPolicy;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.vehicle.entity.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ListingResponse(
    UUID id,
    UUID vehicleId,
    UUID hostId,
    String title,
    String description,
    String city,
    String address,
    BigDecimal latitude,
    BigDecimal longitude,
    BigDecimal basePricePerDay,
    String currency,
    Integer dailyKmLimit,
    Boolean instantBook,
    CancellationPolicy cancellationPolicy,
    ListingStatus status,
    Long version,
    Instant createdAt,
    Instant updatedAt,
    VehicleSummary vehicleSummary,
    List<ExtraResponse> extras,
    Long generatedAvailabilityDays
) {
    public ListingResponse(UUID id, UUID vehicleId, UUID hostId, String title,
                          String description, String city, String address,
                          BigDecimal latitude, BigDecimal longitude,
                          BigDecimal basePricePerDay, String currency,
                          Integer dailyKmLimit, Boolean instantBook,
                          CancellationPolicy cancellationPolicy, ListingStatus status,
                          VehicleSummary vehicleSummary, List<ExtraResponse> extras,
                          Instant createdAt) {
        this(id, vehicleId, hostId, title, description, city, address,
             latitude, longitude, basePricePerDay, currency, dailyKmLimit,
             instantBook, cancellationPolicy, status, null, createdAt, null,
             vehicleSummary, extras, null);
    }

    public static ListingResponse from(Listing listing) {
        return new ListingResponse(
            listing.getId(),
            listing.getVehicleId(),
            listing.getHostId(),
            listing.getTitle(),
            listing.getDescription(),
            listing.getCity(),
            listing.getAddress(),
            listing.getLatitude(),
            listing.getLongitude(),
            listing.getBasePricePerDay(),
            listing.getCurrency(),
            listing.getDailyKmLimit(),
            listing.getInstantBook(),
            listing.getCancellationPolicy(),
            listing.getStatus(),
            listing.getVersion(),
            listing.getCreatedAt(),
            listing.getUpdatedAt(),
            null,
            List.of(),
            null
        );
    }

    public static ListingResponse from(Listing listing, VehicleSummary vehicleSummary,
                                      List<ExtraResponse> extras) {
        return new ListingResponse(
            listing.getId(),
            listing.getVehicleId(),
            listing.getHostId(),
            listing.getTitle(),
            listing.getDescription(),
            listing.getCity(),
            listing.getAddress(),
            listing.getLatitude(),
            listing.getLongitude(),
            listing.getBasePricePerDay(),
            listing.getCurrency(),
            listing.getDailyKmLimit(),
            listing.getInstantBook(),
            listing.getCancellationPolicy(),
            listing.getStatus(),
            listing.getVersion(),
            listing.getCreatedAt(),
            listing.getUpdatedAt(),
            vehicleSummary,
            extras,
            null
        );
    }

    public static ListingResponse from(Listing listing, VehicleSummary vehicleSummary,
                                      List<ExtraResponse> extras, Long generatedAvailabilityDays) {
        return new ListingResponse(
            listing.getId(),
            listing.getVehicleId(),
            listing.getHostId(),
            listing.getTitle(),
            listing.getDescription(),
            listing.getCity(),
            listing.getAddress(),
            listing.getLatitude(),
            listing.getLongitude(),
            listing.getBasePricePerDay(),
            listing.getCurrency(),
            listing.getDailyKmLimit(),
            listing.getInstantBook(),
            listing.getCancellationPolicy(),
            listing.getStatus(),
            listing.getVersion(),
            listing.getCreatedAt(),
            listing.getUpdatedAt(),
            vehicleSummary,
            extras,
            generatedAvailabilityDays
        );
    }

    public record VehicleSummary(
        VehicleCategory category,
        String make,
        String model,
        Integer year,
        TransmissionType transmission,
        FuelType fuelType,
        Integer seats,
        VehicleStatus status
    ) {}
}
