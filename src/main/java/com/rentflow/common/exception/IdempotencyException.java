package com.rentflow.common.exception;

import org.springframework.http.HttpStatus;

public abstract class IdempotencyException extends RentFlowException {

    private final HttpStatus status;

    protected IdempotencyException(String code, String message, HttpStatus status) {
        super(code, message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
