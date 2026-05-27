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
import com.rentflow.payment.provider.CaptureCommand;
import com.rentflow.payment.provider.CaptureResult;
import com.rentflow.payment.provider.VoidCommand;
import com.rentflow.payment.provider.VoidResult;
import com.rentflow.payment.provider.ProviderOrderSnapshot;
import com.rentflow.payment.provider.RefundCommand;
import com.rentflow.payment.provider.RefundResult;
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

    @Test
    void captureMapsCommandToCoreBankRequestAndResult() {
        CoreBankPaymentClient client = mock(CoreBankPaymentClient.class);
        CoreBankPaymentProperties properties = new CoreBankPaymentProperties();
        CoreBankPaymentProvider provider = new CoreBankPaymentProvider(client, properties, new ObjectMapper());
        when(client.captureHold(org.mockito.ArgumentMatchers.any())).thenReturn(new CoreBankCaptureHoldResult(
                new CoreBankCaptureHoldResponse("payment-order-1", "journal-1", "CAPTURED"),
                "{\"paymentOrderId\":\"payment-order-1\",\"journalId\":\"journal-1\",\"status\":\"CAPTURED\"}"));

        CaptureResult result = provider.capture(new CaptureCommand(
                "rentflow:capture:payment:key",
                "payment-order-1",
                new BigDecimal("500000.00"),
                "VND",
                "correlation-1",
                "request-1",
                "session-1",
                "trace-1"));

        ArgumentCaptor<CoreBankCaptureHoldRequest> requestCaptor = ArgumentCaptor.forClass(CoreBankCaptureHoldRequest.class);
        verify(client).captureHold(requestCaptor.capture());
        assertThat(requestCaptor.getValue().idempotencyKey()).isEqualTo("rentflow:capture:payment:key");
        assertThat(requestCaptor.getValue().paymentOrderId()).isEqualTo("payment-order-1");
        assertThat(requestCaptor.getValue().amountMinor()).isEqualTo(500000L);
        assertThat(result.providerJournalId()).isEqualTo("journal-1");
        assertThat(result.providerStatus()).isEqualTo("CAPTURED");
    }

    @Test
    void refundMapsCommandToCoreBankRequestAndResult() {
        CoreBankPaymentClient client = mock(CoreBankPaymentClient.class);
        CoreBankPaymentProperties properties = new CoreBankPaymentProperties();
        CoreBankPaymentProvider provider = new CoreBankPaymentProvider(client, properties, new ObjectMapper());
        when(client.refund(org.mockito.ArgumentMatchers.any())).thenReturn(new CoreBankRefundResult(
                new CoreBankRefundResponse("payment-order-1", "refund-journal-1", "REFUNDED"),
                "{\"paymentOrderId\":\"payment-order-1\",\"refundJournalId\":\"refund-journal-1\",\"status\":\"REFUNDED\"}"));

        RefundResult result = provider.refund(new RefundCommand(
                "rentflow:refund:payment:key",
                "payment-order-1",
                new BigDecimal("200000.00"),
                "VND",
                "Customer cancellation refund",
                "correlation-1",
                "request-1",
                "session-1",
                "trace-1"));

        ArgumentCaptor<CoreBankRefundRequest> requestCaptor = ArgumentCaptor.forClass(CoreBankRefundRequest.class);
        verify(client).refund(requestCaptor.capture());
        assertThat(requestCaptor.getValue().idempotencyKey()).isEqualTo("rentflow:refund:payment:key");
        assertThat(requestCaptor.getValue().paymentOrderId()).isEqualTo("payment-order-1");
        assertThat(requestCaptor.getValue().amountMinor()).isEqualTo(200000L);
        assertThat(result.providerJournalId()).isEqualTo("refund-journal-1");
        assertThat(result.providerStatus()).isEqualTo("REFUNDED");
    }

    @Test
    void findByExternalOrderRefParsesSnapshot() {
        CoreBankPaymentClient client = mock(CoreBankPaymentClient.class);
        CoreBankPaymentProperties properties = new CoreBankPaymentProperties();
        CoreBankPaymentProvider provider = new CoreBankPaymentProvider(client, properties, new ObjectMapper());
        when(client.findOrderByExternalOrderRef("rentflow:booking:1")).thenReturn("""
                {"paymentOrderId":"payment-order-1","holdId":"hold-1","status":"CAPTURED","authorizedAmount":1400000.00,"capturedAmount":1400000.00,"refundedAmount":200000.00,"currency":"VND"}
                """);

        ProviderOrderSnapshot snapshot = provider.findByExternalOrderRef("rentflow:booking:1");

        assertThat(snapshot.paymentOrderId()).isEqualTo("payment-order-1");
        assertThat(snapshot.holdId()).isEqualTo("hold-1");
        assertThat(snapshot.status()).isEqualTo("CAPTURED");
        assertThat(snapshot.authorizedAmount()).isEqualByComparingTo("1400000.00");
        assertThat(snapshot.refundedAmount()).isEqualByComparingTo("200000.00");
    }
}
