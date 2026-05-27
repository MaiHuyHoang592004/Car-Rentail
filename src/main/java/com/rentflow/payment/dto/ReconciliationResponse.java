package com.rentflow.payment.dto;

import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ReconciliationResponse(
        LocalSnapshot local,
        ProviderSnapshot provider,
        MismatchFlags mismatchFlags,
        boolean requiresReconciliation
) {
    public record LocalSnapshot(
            UUID paymentId,
            UUID bookingId,
            PaymentProviderType provider,
            PaymentStatus paymentStatus,
            String providerStatus,
            String externalOrderRef,
            String providerPaymentOrderId,
            String providerHoldId,
            BigDecimal authorizedAmount,
            BigDecimal capturedAmount,
            BigDecimal refundedAmount,
            String currency
    ) {
    }

    public record ProviderSnapshot(
            String status,
            String paymentOrderId,
            String holdId,
            BigDecimal authorizedAmount,
            BigDecimal capturedAmount,
            BigDecimal refundedAmount,
            String currency
    ) {
    }

    public record MismatchFlags(
            boolean statusMismatch,
            boolean amountMismatch,
            boolean referenceMismatch
    ) {
    }
}
