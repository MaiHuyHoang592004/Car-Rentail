package com.rentflow.common.exception;

public class ValidationException extends RentFlowException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }
}
