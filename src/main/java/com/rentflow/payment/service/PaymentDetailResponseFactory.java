package com.rentflow.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.booking.entity.Booking;
import com.rentflow.payment.dto.AuthorizePaymentResponse;
import com.rentflow.payment.dto.PaymentDetailResponse;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentTransaction;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class PaymentDetailResponseFactory {

    private final ObjectMapper objectMapper;

    public PaymentDetailResponseFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PaymentDetailResponse create(
            Booking booking,
            BookingPayment payment,
            List<PaymentTransaction> transactions) {
        return new PaymentDetailResponse(
                new PaymentDetailResponse.BookingSummary(
                        booking.getId(),
                        booking.getCustomerId(),
                        booking.getHostId(),
                        booking.getStatus(),
                        booking.getPickupDate(),
                        booking.getReturnDate()),
                new PaymentDetailResponse.PaymentSummary(
                        payment.getId(),
                        payment.getSelectedBankId(),
                        payment.getPaymentMethod(),
                        payment.getProvider(),
                        payment.getStatus(),
                        payment.getAuthorizedAmount(),
                        payment.getCapturedAmount(),
                        payment.getRefundedAmount(),
                        payment.getCurrency(),
                        payment.getExternalOrderRef(),
                        payment.getProviderPaymentOrderId(),
                        payment.getProviderHoldId(),
                        payment.getProviderStatus(),
                        extractTransferInstruction(payment.getProviderMetadata())),
                transactions.stream().map(tx -> new PaymentDetailResponse.TransactionSummary(
                        tx.getId(),
                        tx.getType(),
                        tx.getStatus(),
                        tx.getAmount(),
                        tx.getCurrency(),
                        tx.getProvider(),
                        tx.getProviderRequestId(),
                        tx.getProviderRef(),
                        tx.getProviderJournalId(),
                        tx.getProviderErrorCode(),
                        tx.getProviderErrorMessage(),
                        tx.getCreatedAt())).toList());
    }

    private AuthorizePaymentResponse.TransferInstructionResponse extractTransferInstruction(String providerMetadataJson) {
        if (providerMetadataJson == null || providerMetadataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(providerMetadataJson);
            JsonNode node = root.path("transferInstruction");
            if (node.isMissingNode() || node.isNull()) {
                return null;
            }
            java.math.BigDecimal amount = node.hasNonNull("amount") ? node.path("amount").decimalValue() : null;
            return new AuthorizePaymentResponse.TransferInstructionResponse(
                    node.path("bankCode").asText(null),
                    node.path("bankBin").asText(null),
                    node.path("accountNumber").asText(null),
                    node.path("accountName").asText(null),
                    amount,
                    node.path("content").asText(null),
                    node.path("qrPayload").asText(null));
        } catch (IOException e) {
            return null;
        }
    }
}
