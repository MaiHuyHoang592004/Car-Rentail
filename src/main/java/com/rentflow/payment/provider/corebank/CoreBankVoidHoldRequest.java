package com.rentflow.payment.provider.corebank;

public record CoreBankVoidHoldRequest(
        String idempotencyKey,
        String holdId,
        String actor,
        String correlationId,
        String requestId,
        String sessionId,
        String traceId
) {
}
