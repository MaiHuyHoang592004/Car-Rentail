package com.rentflow.payment.provider;

import java.math.BigDecimal;

public record RefundCommand(
        String providerIdempotencyKey,
        String providerPaymentOrderId,
        BigDecimal amount,
        String currency,
        String reason,
        String correlationId,
        String requestId,
        String sessionId,
        String traceId
) {
}
