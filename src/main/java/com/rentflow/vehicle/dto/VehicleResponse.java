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
    IdentifierIntegrity identifierIntegrity,
    String primaryPhotoUrl,
    List<Photo> photos,
    Instant createdAt,
    Instant updatedAt
) {
    public record IdentifierIntegrity(
        boolean plateNumberReadable,
        boolean vinReadable,
        boolean hasUnreadableEncryptedFields
    ) {}

    public record Photo(
        UUID id,
        UUID fileId,
        boolean primary,
        int displayOrder,
        String signedUrl
    ) {}

    public static VehicleResponse from(
            Vehicle vehicle,
            String decryptedPlate,
            boolean plateNumberReadable,
            String decryptedVin,
            boolean vinReadable) {
        return from(vehicle, decryptedPlate, plateNumberReadable, decryptedVin, vinReadable, List.of());
    }

    public static VehicleResponse from(
            Vehicle vehicle,
            String decryptedPlate,
            boolean plateNumberReadable,
            String decryptedVin,
            boolean vinReadable,
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
            new IdentifierIntegrity(
                    plateNumberReadable,
                    vinReadable,
                    !plateNumberReadable || !vinReadable),
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
