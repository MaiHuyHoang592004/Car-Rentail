package com.rentflow.common.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyConflictException extends IdempotencyException {

    public IdempotencyKeyConflictException() {
        super("IDEMPOTENCY_KEY_CONFLICT",
                "Same idempotency key was used with a different request body",
                HttpStatus.CONFLICT);
    }
}
