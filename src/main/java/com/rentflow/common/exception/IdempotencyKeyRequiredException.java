package com.rentflow.common.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyRequiredException extends IdempotencyException {

    public IdempotencyKeyRequiredException(String message) {
        super("IDEMPOTENCY_KEY_REQUIRED", message, HttpStatus.BAD_REQUEST);
    }
}
