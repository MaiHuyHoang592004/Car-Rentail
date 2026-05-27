package com.rentflow.payment.provider;

import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;

import java.math.BigDecimal;

public record AuthorizeResult(
        PaymentProviderType provider,
        PaymentStatus paymentStatus,
        BigDecimal authorizedAmount,
        TransferInstruction transferInstruction,
        String providerStatus,
        String providerPaymentOrderId,
        String providerHoldId,
        String providerMetadataJson
) {
}
