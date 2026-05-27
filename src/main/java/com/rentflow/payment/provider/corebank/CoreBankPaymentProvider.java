package com.rentflow.payment.provider.corebank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.payment.config.CoreBankPaymentProperties;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.provider.AuthorizeCommand;
import com.rentflow.payment.provider.AuthorizeResult;
import com.rentflow.payment.provider.PaymentProvider;
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
}
