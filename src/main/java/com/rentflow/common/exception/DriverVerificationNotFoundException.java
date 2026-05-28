package com.rentflow.common.exception;

public class DriverVerificationNotFoundException extends ResourceNotFoundException {

    public DriverVerificationNotFoundException(String verificationId) {
        super("DRIVER_VERIFICATION_NOT_FOUND", "Driver verification", verificationId);
    }
}
