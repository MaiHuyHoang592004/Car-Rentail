package com.rentflow.payment.dto;

import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AuthorizePaymentResponse(
        BookingSummary booking,
        PaymentSummary payment
) {
    public record BookingSummary(
            UUID id,
            BookingStatus status,
            LocalDate pickupDate,
            LocalDate returnDate,
            BigDecimal totalAmount,
            String currency
    ) {
    }

    public record PaymentSummary(
            UUID id,
            PaymentStatus status,
            PaymentMethod paymentMethod,
            PaymentProviderType provider,
            String externalOrderRef,
            String providerPaymentOrderId,
            String providerHoldId,
            BigDecimal authorizedAmount,
            BigDecimal capturedAmount,
            BigDecimal refundedAmount,
            String currency,
            TransferInstructionResponse transferInstruction
    ) {
    }

    public record TransferInstructionResponse(
            String bankCode,
            String bankBin,
            String accountNumber,
            String accountName,
            BigDecimal amount,
            String content,
            String qrPayload
    ) {
    }
}
