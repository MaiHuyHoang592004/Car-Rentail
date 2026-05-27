package com.rentflow.payment.provider.corebank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.booking.entity.Booking;
import com.rentflow.payment.config.CoreBankPaymentProperties;
import com.rentflow.payment.entity.PaymentBank;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.provider.AuthorizeCommand;
import com.rentflow.payment.provider.AuthorizeResult;
import com.rentflow.payment.provider.VoidCommand;
import com.rentflow.payment.provider.VoidResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class CoreBankPaymentProviderTest {

    @Test
    void authorizeMapsCommandToCoreBankRequestAndResult() {
        CoreBankPaymentClient client = mock(CoreBankPaymentClient.class);
        CoreBankPaymentProperties properties = new CoreBankPaymentProperties();
        properties.setPayeeAccountId("escrow-account");
        CoreBankPaymentProvider provider = new CoreBankPaymentProvider(client, properties, new ObjectMapper());

        Booking booking = new Booking();
        booking.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        PaymentBank bank = new PaymentBank();
        bank.setCode("COREBANK");
        bank.setPaymentMethod(PaymentMethod.COREBANK_TRANSFER);
        bank.setProvider(PaymentProviderType.COREBANK);
        when(client.authorizeHold(org.mockito.ArgumentMatchers.any())).thenReturn(new CoreBankAuthorizeHoldResult(
                new CoreBankAuthorizeHoldResponse("payment-order-1", "hold-1", "AUTHORIZED"),
                "{\"paymentOrderId\":\"payment-order-1\",\"holdId\":\"hold-1\",\"status\":\"AUTHORIZED\"}"));

        AuthorizeResult result = provider.authorize(new AuthorizeCommand(
                booking,
                bank,
                PaymentMethod.COREBANK_TRANSFER,
                new BigDecimal("1400000.00"),
                "VND",
                "rentflow:booking:" + booking.getId(),
                "rentflow:authorize:" + booking.getId() + ":key",
                "payer-account-1",
                "correlation-1",
                "request-1",
                booking.getId().toString(),
                "trace-1"));

        ArgumentCaptor<CoreBankAuthorizeHoldRequest> requestCaptor = ArgumentCaptor.forClass(CoreBankAuthorizeHoldRequest.class);
        verify(client).authorizeHold(requestCaptor.capture());
        assertThat(requestCaptor.getValue().payerAccountId()).isEqualTo("payer-account-1");
        assertThat(requestCaptor.getValue().payeeAccountId()).isEqualTo("escrow-account");
        assertThat(requestCaptor.getValue().externalOrderRef()).isEqualTo("rentflow:booking:" + booking.getId());
        assertThat(requestCaptor.getValue().amountMinor()).isEqualTo(1_400_000L);
        assertThat(requestCaptor.getValue().idempotencyKey()).isEqualTo("rentflow:authorize:" + booking.getId() + ":key");

        assertThat(result.provider()).isEqualTo(PaymentProviderType.COREBANK);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(result.authorizedAmount()).isEqualByComparingTo("1400000.00");
        assertThat(result.providerPaymentOrderId()).isEqualTo("payment-order-1");
        assertThat(result.providerHoldId()).isEqualTo("hold-1");
        assertThat(result.providerMetadataJson()).contains("paymentOrderId");
    }

    @Test
    void voidAuthorizationMapsCommandToCoreBankRequestAndResult() {
        CoreBankPaymentClient client = mock(CoreBankPaymentClient.class);
        CoreBankPaymentProperties properties = new CoreBankPaymentProperties();
        CoreBankPaymentProvider provider = new CoreBankPaymentProvider(client, properties, new ObjectMapper());
        when(client.voidHold(org.mockito.ArgumentMatchers.any())).thenReturn(new CoreBankVoidHoldResult(
                new CoreBankVoidHoldResponse("hold-1", "VOIDED"),
                "{\"holdId\":\"hold-1\",\"status\":\"VOIDED\"}"));

        VoidResult result = provider.voidAuthorization(new VoidCommand(
                "rentflow:void:payment:key",
                "hold-1",
                "correlation-1",
                "request-1",
                "session-1",
                "trace-1"));

        ArgumentCaptor<CoreBankVoidHoldRequest> requestCaptor = ArgumentCaptor.forClass(CoreBankVoidHoldRequest.class);
        verify(client).voidHold(requestCaptor.capture());
        assertThat(requestCaptor.getValue().idempotencyKey()).isEqualTo("rentflow:void:payment:key");
        assertThat(requestCaptor.getValue().holdId()).isEqualTo("hold-1");
        assertThat(result.providerStatus()).isEqualTo("VOIDED");
        assertThat(result.providerMetadataJson()).contains("\"status\":\"VOIDED\"");
    }
}
