package com.rentflow.vehicle.dto;

import com.rentflow.vehicle.entity.FuelType;
import com.rentflow.vehicle.entity.TransmissionType;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleCategory;
import com.rentflow.vehicle.entity.VehicleStatus;

import java.time.Instant;
import java.util.UUID;

public record VehicleResponse(
    UUID id,
    VehicleCategory category,
    String make,
    String model,
    Integer year,
    TransmissionType transmission,
    FuelType fuelType,
    Integer seats,
    VehicleStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
            vehicle.getId(),
            vehicle.getCategory(),
            vehicle.getMake(),
            vehicle.getModel(),
            vehicle.getManufactureYear(),
            vehicle.getTransmission(),
            vehicle.getFuelType(),
            vehicle.getSeats(),
            vehicle.getStatus(),
            vehicle.getCreatedAt(),
            vehicle.getUpdatedAt()
        );
    }
}
