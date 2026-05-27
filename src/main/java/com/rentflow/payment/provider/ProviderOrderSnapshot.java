package com.rentflow.payment.provider;

import java.math.BigDecimal;

public record ProviderOrderSnapshot(
        String status,
        String paymentOrderId,
        String holdId,
        BigDecimal authorizedAmount,
        BigDecimal capturedAmount,
        BigDecimal refundedAmount,
        String currency,
        String rawProviderJson
) {
}
