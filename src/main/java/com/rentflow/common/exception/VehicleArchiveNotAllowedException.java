package com.rentflow.common.exception;

public class VehicleArchiveNotAllowedException extends RentFlowException {

    public VehicleArchiveNotAllowedException(String message) {
        super("VEHICLE_ARCHIVE_NOT_ALLOWED", message);
    }
}
