package com.rentflow.listing.dto;

import com.rentflow.vehicle.entity.FuelType;
import com.rentflow.vehicle.entity.TransmissionType;
import com.rentflow.vehicle.entity.VehicleCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ListingSearchCriteria(
    String city,
    List<VehicleCategory> categories,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    Integer seats,
    TransmissionType transmission,
    FuelType fuelType,
    LocalDate pickupDate,
    LocalDate returnDate
) {}
