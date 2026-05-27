package com.rentflow.payment.provider;

public record VoidCommand(
        String providerIdempotencyKey,
        String providerHoldId,
        String correlationId,
        String requestId,
        String sessionId,
        String traceId
) {
}
