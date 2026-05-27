package com.rentflow.payment.provider.corebank;

public record CoreBankAuthorizeHoldRequest(
        String idempotencyKey,
        String payerAccountId,
        String payeeAccountId,
        long amountMinor,
        String currency,
        String paymentType,
        String description,
        String externalOrderRef,
        String actor,
        String correlationId,
        String requestId,
        String sessionId,
        String traceId
) {
}
