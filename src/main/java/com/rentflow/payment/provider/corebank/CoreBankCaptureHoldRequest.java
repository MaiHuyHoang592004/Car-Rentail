package com.rentflow.payment.provider.corebank;

public record CoreBankCaptureHoldRequest(
        String idempotencyKey,
        String paymentOrderId,
        long amountMinor,
        String currency,
        String actor,
        String correlationId,
        String requestId,
        String sessionId,
        String traceId
) {
}
