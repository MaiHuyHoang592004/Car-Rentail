package com.rentflow.common.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyAlreadyProcessingException extends IdempotencyException {

    public IdempotencyAlreadyProcessingException() {
        super("REQUEST_ALREADY_PROCESSING",
                "Request with same idempotency key is still processing",
                HttpStatus.CONFLICT);
    }
}
