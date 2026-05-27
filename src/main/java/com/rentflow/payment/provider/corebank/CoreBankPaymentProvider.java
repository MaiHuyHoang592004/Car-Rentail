package com.rentflow.payment.provider.corebank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.payment.config.CoreBankPaymentProperties;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.provider.AuthorizeCommand;
import com.rentflow.payment.provider.AuthorizeResult;
import com.rentflow.payment.provider.CaptureCommand;
import com.rentflow.payment.provider.CaptureResult;
import com.rentflow.payment.provider.PaymentProvider;
import com.rentflow.payment.provider.ProviderOrderSnapshot;
import com.rentflow.payment.provider.RefundCommand;
import com.rentflow.payment.provider.RefundResult;
import com.rentflow.payment.provider.VoidCommand;
import com.rentflow.payment.provider.VoidResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CoreBankPaymentProvider implements PaymentProvider {

    private static final String PAYMENT_TYPE = "MERCHANT_PAYMENT";
    private static final String DESCRIPTION = "RentFlow booking payment";
    private static final String ACTOR = "rentflow";

    private final CoreBankPaymentClient coreBankPaymentClient;
    private final CoreBankPaymentProperties properties;
    private final ObjectMapper objectMapper;

    public CoreBankPaymentProvider(
            CoreBankPaymentClient coreBankPaymentClient,
            CoreBankPaymentProperties properties,
            ObjectMapper objectMapper) {
        this.coreBankPaymentClient = coreBankPaymentClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.COREBANK;
    }

    @Override
    public AuthorizeResult authorize(AuthorizeCommand command) {
        CoreBankAuthorizeHoldResult result = coreBankPaymentClient.authorizeHold(new CoreBankAuthorizeHoldRequest(
                command.providerIdempotencyKey(),
                command.payerAccountId(),
                properties.getPayeeAccountId(),
                toAmountMinor(command.totalAmount(), command.currency()),
                command.currency(),
                PAYMENT_TYPE,
                DESCRIPTION,
                command.externalOrderRef(),
                ACTOR,
                command.correlationId(),
                command.requestId(),
                command.sessionId(),
                command.traceId()));

        return new AuthorizeResult(
                PaymentProviderType.COREBANK,
                PaymentStatus.AUTHORIZED,
                command.totalAmount(),
                null,
                result.response().status(),
                result.response().paymentOrderId(),
                result.response().holdId(),
                result.rawResponseJson());
    }

    @Override
    public VoidResult voidAuthorization(VoidCommand command) {
        CoreBankVoidHoldResult result = coreBankPaymentClient.voidHold(new CoreBankVoidHoldRequest(
                command.providerIdempotencyKey(),
                command.providerHoldId(),
                ACTOR,
                command.correlationId(),
                command.requestId(),
                command.sessionId(),
                command.traceId()));
        return new VoidResult(result.response().status(), result.rawResponseJson());
    }

    @Override
    public CaptureResult capture(CaptureCommand command) {
        CoreBankCaptureHoldResult result = coreBankPaymentClient.captureHold(new CoreBankCaptureHoldRequest(
                command.providerIdempotencyKey(),
                command.providerPaymentOrderId(),
                toAmountMinor(command.amount(), command.currency()),
                command.currency(),
                ACTOR,
                command.correlationId(),
                command.requestId(),
                command.sessionId(),
                command.traceId()));
        return new CaptureResult(
                result.response().status(),
                result.response().journalId(),
                result.rawResponseJson());
    }

    @Override
    public RefundResult refund(RefundCommand command) {
        CoreBankRefundResult result = coreBankPaymentClient.refund(new CoreBankRefundRequest(
                command.providerIdempotencyKey(),
                command.providerPaymentOrderId(),
                toAmountMinor(command.amount(), command.currency()),
                command.currency(),
                command.reason(),
                ACTOR,
                command.correlationId(),
                command.requestId(),
                command.sessionId(),
                command.traceId()));
        return new RefundResult(
                result.response().status(),
                result.response().refundJournalId(),
                result.rawResponseJson());
    }

    @Override
    public ProviderOrderSnapshot findByExternalOrderRef(String externalOrderRef) {
        String rawJson = coreBankPaymentClient.findOrderByExternalOrderRef(externalOrderRef);
        JsonNode orderNode = orderNode(rawJson);
        return new ProviderOrderSnapshot(
                text(orderNode, "status"),
                text(orderNode, "paymentOrderId", "payment_order_id"),
                text(orderNode, "holdId", "hold_id"),
                decimal(orderNode, "authorizedAmount", "authorized_amount", "amount"),
                decimal(orderNode, "capturedAmount", "captured_amount"),
                decimal(orderNode, "refundedAmount", "refunded_amount"),
                text(orderNode, "currency"),
                rawJson);
    }

    private long toAmountMinor(BigDecimal totalAmount, String currency) {
        BigDecimal normalized = "VND".equalsIgnoreCase(currency)
                ? totalAmount
                : totalAmount.movePointRight(2);
        return normalized.longValueExact();
    }

    public String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize CoreBank payload", e);
        }
    }

    private JsonNode orderNode(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root.isArray()) {
                return root.isEmpty() ? root : root.get(0);
            }
            JsonNode items = root.path("items");
            if (items.isArray()) {
                return items.isEmpty() ? items : items.get(0);
            }
            return root;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse CoreBank order query response", e);
        }
    }

    private String text(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (!value.isMissingNode() && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    private BigDecimal decimal(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (!value.isMissingNode() && !value.isNull()) {
                return value.decimalValue();
            }
        }
        return null;
    }
}
