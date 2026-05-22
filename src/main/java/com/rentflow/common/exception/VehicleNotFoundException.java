package com.rentflow.common.exception;

public class VehicleNotFoundException extends ResourceNotFoundException {

    public VehicleNotFoundException(String vehicleId) {
        super("VEHICLE_NOT_FOUND", "Vehicle", vehicleId);
    }
}
