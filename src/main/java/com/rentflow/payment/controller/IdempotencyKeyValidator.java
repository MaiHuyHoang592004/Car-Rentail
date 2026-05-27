package com.rentflow.payment.controller;

import com.rentflow.common.exception.IdempotencyKeyRequiredException;
import com.rentflow.common.exception.ValidationException;

import java.util.regex.Pattern;

final class IdempotencyKeyValidator {

    private static final Pattern UUID_V4_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private IdempotencyKeyValidator() {
    }

    static void validate(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequiredException("Idempotency-Key header is required");
        }
        if (!UUID_V4_PATTERN.matcher(idempotencyKey).matches()) {
            throw new ValidationException("Idempotency-Key must be a UUID-v4 value");
        }
    }
}
