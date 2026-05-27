package com.rentflow.payment.dto;

import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransactionStatus;
import com.rentflow.payment.entity.PaymentTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentDetailResponse(
        BookingSummary booking,
        PaymentSummary payment,
        List<TransactionSummary> transactions
) {
    public record BookingSummary(
            UUID id,
            UUID customerId,
            UUID hostId,
            BookingStatus status,
            LocalDate pickupDate,
            LocalDate returnDate
    ) {
    }

    public record PaymentSummary(
            UUID id,
            UUID selectedBankId,
            PaymentMethod paymentMethod,
            PaymentProviderType provider,
            PaymentStatus status,
            BigDecimal authorizedAmount,
            BigDecimal capturedAmount,
            BigDecimal refundedAmount,
            String currency,
            String externalOrderRef,
            String providerPaymentOrderId,
            String providerHoldId,
            String providerStatus,
            AuthorizePaymentResponse.TransferInstructionResponse transferInstruction
    ) {
    }

    public record TransactionSummary(
            UUID id,
            PaymentTransactionType type,
            PaymentTransactionStatus status,
            BigDecimal amount,
            String currency,
            PaymentProviderType provider,
            String providerRequestId,
            String providerRef,
            String providerJournalId,
            String providerErrorCode,
            String providerErrorMessage,
            Instant createdAt
    ) {
    }
}
