package com.rentflow.common.idempotency.service;

import java.util.UUID;

public sealed interface IdempotencyResolution
        permits IdempotencyResolution.Proceed, IdempotencyResolution.Replay {

    static Proceed proceed(UUID idempotencyKeyId) {
        return new Proceed(idempotencyKeyId);
    }

    static Replay replay(int responseStatus, String responseBodyJson) {
        return new Replay(responseStatus, responseBodyJson);
    }

    record Proceed(UUID idempotencyKeyId) implements IdempotencyResolution {}

    record Replay(int responseStatus, String responseBodyJson) implements IdempotencyResolution {}
}
