package com.rentflow.vehicle.dto;

import com.rentflow.vehicle.entity.FuelType;
import com.rentflow.vehicle.entity.TransmissionType;
import com.rentflow.vehicle.entity.VehicleCategory;
import com.rentflow.vehicle.entity.VehicleStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateVehicleRequest(
    VehicleCategory category,

    @Size(max = 50, message = "Make must be at most 50 characters")
    String make,

    @Size(max = 50, message = "Model must be at most 50 characters")
    String model,

    @Min(value = 1990, message = "Year must be 1990 or later")
    @Max(value = 2030, message = "Year must be 2030 or earlier")
    Integer year,

    TransmissionType transmission,

    FuelType fuelType,

    @Min(value = 1, message = "Seats must be at least 1")
    @Max(value = 30, message = "Seats must be at most 30")
    Integer seats,

    VehicleStatus status,

    @Size(max = 100, message = "City must be at most 100 characters")
    String city
) {}
