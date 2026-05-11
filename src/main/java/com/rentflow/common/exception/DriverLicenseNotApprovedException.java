package com.rentflow.common.exception;

public class DriverLicenseNotApprovedException extends RentFlowException {

    public DriverLicenseNotApprovedException() {
        super("DRIVER_LICENSE_NOT_APPROVED", "Driver license is not approved");
    }
}
