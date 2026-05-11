package com.rentflow.vehicle.dto;

import com.rentflow.vehicle.entity.FuelType;
import com.rentflow.vehicle.entity.TransmissionType;
import com.rentflow.vehicle.entity.VehicleCategory;
import com.rentflow.vehicle.entity.VehicleStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVehicleRequest(
    @NotNull(message = "Category is required")
    VehicleCategory category,

    @NotBlank(message = "Make is required")
    @Size(max = 50, message = "Make must be at most 50 characters")
    String make,

    @NotBlank(message = "Model is required")
    @Size(max = 50, message = "Model must be at most 50 characters")
    String model,

    @NotNull(message = "Year is required")
    @Min(value = 1990, message = "Year must be 1990 or later")
    @Max(value = 2030, message = "Year must be 2030 or earlier")
    Integer year,

    @NotBlank(message = "Plate number is required")
    @Size(max = 20, message = "Plate number must be at most 20 characters")
    String plateNumber,

    String vin,

    @NotNull(message = "Transmission is required")
    TransmissionType transmission,

    @NotNull(message = "Fuel type is required")
    FuelType fuelType,

    @NotNull(message = "Number of seats is required")
    @Min(value = 1, message = "Seats must be at least 1")
    @Max(value = 30, message = "Seats must be at most 30")
    Integer seats,

    VehicleStatus status
) {}
