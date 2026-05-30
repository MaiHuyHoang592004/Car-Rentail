package com.rentflow.vehicle.dto;

import com.rentflow.vehicle.entity.FuelType;
import com.rentflow.vehicle.entity.TransmissionType;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleCategory;
import com.rentflow.vehicle.entity.VehicleStatus;

import java.time.Instant;
import java.util.List;
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
    String city,
    String plateNumber,
    String vin,
    String primaryPhotoUrl,
    List<Photo> photos,
    Instant createdAt,
    Instant updatedAt
) {
    public record Photo(
        UUID id,
        UUID fileId,
        boolean primary,
        int displayOrder,
        String signedUrl
    ) {}

    public static VehicleResponse from(Vehicle vehicle, String decryptedPlate, String decryptedVin) {
        return from(vehicle, decryptedPlate, decryptedVin, List.of());
    }

    public static VehicleResponse from(
            Vehicle vehicle,
            String decryptedPlate,
            String decryptedVin,
            List<Photo> photos) {
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
            vehicle.getCity(),
            decryptedPlate,
            decryptedVin,
            photos.stream().filter(Photo::primary).findFirst()
                    .or(() -> photos.stream().findFirst())
                    .map(Photo::signedUrl)
                    .orElse(null),
            photos,
            vehicle.getCreatedAt(),
            vehicle.getUpdatedAt()
        );
    }
}
