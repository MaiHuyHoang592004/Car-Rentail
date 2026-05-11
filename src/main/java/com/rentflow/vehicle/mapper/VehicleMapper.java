package com.rentflow.vehicle.mapper;

import com.rentflow.vehicle.dto.CreateVehicleRequest;
import com.rentflow.vehicle.dto.UpdateVehicleRequest;
import com.rentflow.vehicle.dto.VehicleResponse;
import com.rentflow.vehicle.entity.Vehicle;
import org.springframework.stereotype.Component;

@Component
public class VehicleMapper {

    public Vehicle toEntity(CreateVehicleRequest request, String encryptedPlate, String plateHash,
                            String encryptedVin, String vinHash) {
        Vehicle vehicle = new Vehicle();
        vehicle.setCategory(request.category());
        vehicle.setMake(request.make());
        vehicle.setModel(request.model());
        vehicle.setManufactureYear(request.year());
        vehicle.setTransmission(request.transmission());
        vehicle.setFuelType(request.fuelType());
        vehicle.setSeats(request.seats());
        vehicle.setPlateNumberEncrypted(encryptedPlate);
        vehicle.setPlateNumberHash(plateHash);
        vehicle.setVinEncrypted(encryptedVin);
        vehicle.setVinHash(vinHash);
        vehicle.setStatus(request.status() != null ? request.status() : com.rentflow.vehicle.entity.VehicleStatus.ACTIVE);
        return vehicle;
    }

    public void applyUpdate(Vehicle vehicle, UpdateVehicleRequest request) {
        if (request.category() != null) vehicle.setCategory(request.category());
        if (request.make() != null) vehicle.setMake(request.make());
        if (request.model() != null) vehicle.setModel(request.model());
        if (request.year() != null) vehicle.setManufactureYear(request.year());
        if (request.transmission() != null) vehicle.setTransmission(request.transmission());
        if (request.fuelType() != null) vehicle.setFuelType(request.fuelType());
        if (request.seats() != null) vehicle.setSeats(request.seats());
    }

    public VehicleResponse toResponse(Vehicle vehicle) {
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
