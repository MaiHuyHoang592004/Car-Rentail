package com.rentflow.payment.provider;

import com.rentflow.booking.entity.Booking;
import com.rentflow.payment.entity.PaymentBank;
import com.rentflow.payment.entity.PaymentMethod;

import java.math.BigDecimal;

public record AuthorizeCommand(
        Booking booking,
        PaymentBank bank,
        PaymentMethod paymentMethod,
        BigDecimal totalAmount,
        String currency,
        String externalOrderRef,
        String providerIdempotencyKey,
        String payerAccountId,
        String correlationId,
        String requestId,
        String sessionId,
        String traceId
) {
}
