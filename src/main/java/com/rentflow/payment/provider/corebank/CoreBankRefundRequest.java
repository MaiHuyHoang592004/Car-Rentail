package com.rentflow.payment.provider.corebank;

public record CoreBankRefundRequest(
        String idempotencyKey,
        String paymentOrderId,
        long amountMinor,
        String currency,
        String reason,
        String actor,
        String correlationId,
        String requestId,
        String sessionId,
        String traceId
) {
}
