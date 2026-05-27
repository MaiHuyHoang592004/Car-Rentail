package com.rentflow.payment.service;

import com.rentflow.booking.entity.Booking;
import com.rentflow.payment.dto.AuthorizePaymentResponse;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.provider.TransferInstruction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class AuthorizePaymentResponseFactory {

    public AuthorizePaymentResponse create(
            Booking booking,
            BookingPayment payment,
            BigDecimal totalAmount,
            String currency,
            TransferInstruction transferInstruction) {
        return new AuthorizePaymentResponse(
                new AuthorizePaymentResponse.BookingSummary(
                        booking.getId(),
                        booking.getStatus(),
                        booking.getPickupDate(),
                        booking.getReturnDate(),
                        totalAmount,
                        currency),
                new AuthorizePaymentResponse.PaymentSummary(
                        payment.getId(),
                        payment.getStatus(),
                        payment.getPaymentMethod(),
                        payment.getProvider(),
                        payment.getExternalOrderRef(),
                        payment.getProviderPaymentOrderId(),
                        payment.getProviderHoldId(),
                        payment.getAuthorizedAmount(),
                        payment.getCapturedAmount(),
                        payment.getRefundedAmount(),
                        payment.getCurrency(),
                        transferInstruction == null ? null : new AuthorizePaymentResponse.TransferInstructionResponse(
                                transferInstruction.bankCode(),
                                transferInstruction.bankBin(),
                                transferInstruction.accountNumber(),
                                transferInstruction.accountName(),
                                transferInstruction.amount(),
                                transferInstruction.content(),
                                transferInstruction.qrPayload())));
    }
}
