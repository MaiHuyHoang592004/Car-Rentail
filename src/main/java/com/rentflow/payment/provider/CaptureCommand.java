package com.rentflow.payment.provider;

import java.math.BigDecimal;

public record CaptureCommand(
        String providerIdempotencyKey,
        String providerPaymentOrderId,
        BigDecimal amount,
        String currency,
        String correlationId,
        String requestId,
        String sessionId,
        String traceId
) {
}
