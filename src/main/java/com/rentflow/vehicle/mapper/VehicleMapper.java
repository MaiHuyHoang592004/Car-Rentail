package com.rentflow.vehicle.mapper;

import com.rentflow.common.util.EncryptionUtil;
import com.rentflow.vehicle.dto.CreateVehicleRequest;
import com.rentflow.vehicle.dto.UpdateVehicleRequest;
import com.rentflow.vehicle.dto.VehicleResponse;
import com.rentflow.vehicle.entity.Vehicle;
import org.springframework.stereotype.Component;

@Component
public class VehicleMapper {

    private final EncryptionUtil encryptionUtil;

    public VehicleMapper(EncryptionUtil encryptionUtil) {
        this.encryptionUtil = encryptionUtil;
    }

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
        vehicle.setCity(request.city());
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
        if (request.city() != null) vehicle.setCity(request.city());
        if (request.status() != null) vehicle.setStatus(request.status());
    }

    public VehicleResponse toResponse(Vehicle vehicle) {
        return VehicleResponse.from(
            vehicle,
            encryptionUtil.decrypt(vehicle.getPlateNumberEncrypted()),
            encryptionUtil.decrypt(vehicle.getVinEncrypted())
        );
    }
}
