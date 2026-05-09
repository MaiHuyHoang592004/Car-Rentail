package com.rentflow.common.exception;

public class IdempotencyException extends RentFlowException {

    public IdempotencyException(String code, String message) {
        super(code, message);
    }
}
