package com.rentflow.common.exception;

public class VehicleNotFoundException extends RentFlowException {

    public VehicleNotFoundException(String vehicleId) {
        super("VEHICLE_NOT_FOUND", "Vehicle not found: " + vehicleId);
    }
}
