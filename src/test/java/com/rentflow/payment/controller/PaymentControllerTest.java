package com.rentflow.payment.controller;

import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.GlobalExceptionHandler;
import com.rentflow.payment.dto.CapturePaymentRequest;
import com.rentflow.payment.dto.PaymentDetailResponse;
import com.rentflow.payment.dto.ReconciliationResponse;
import com.rentflow.payment.dto.RefundPaymentRequest;
import com.rentflow.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentControllerTest {

    private static final UUID PAYMENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String VALID_IDEMPOTENCY_KEY = "8b71f8d2-9e1d-4f7a-bbe6-334c3816df91";

    private PaymentService paymentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentService))
                .setControllerAdvice(new GlobalExceptionHandler(new CorrelationIdHelper()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void captureMissingIdempotencyKeyReturnsRequiredError() throws Exception {
        mockMvc.perform(post("/api/v1/payments/{paymentId}/capture", PAYMENT_ID)
                        .contentType("application/json")
                        .content("""
                                {"amount":1000.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void captureDelegatesAndReturnsResponse() throws Exception {
        PaymentDetailResponse response = new PaymentDetailResponse(null, null, List.of());
        when(paymentService.capturePayment(eq(PAYMENT_ID), eq(VALID_IDEMPOTENCY_KEY), any(CapturePaymentRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/{paymentId}/capture", PAYMENT_ID)
                        .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                        .contentType("application/json")
                        .content("""
                                {"amount":1000.00}
                                """))
                .andExpect(status().isOk());

        verify(paymentService).capturePayment(eq(PAYMENT_ID), eq(VALID_IDEMPOTENCY_KEY), any(CapturePaymentRequest.class));
    }

    @Test
    void voidDelegatesAndReturnsResponse() throws Exception {
        when(paymentService.voidPayment(PAYMENT_ID, VALID_IDEMPOTENCY_KEY))
                .thenReturn(new PaymentDetailResponse(null, null, List.of()));

        mockMvc.perform(post("/api/v1/payments/{paymentId}/void", PAYMENT_ID)
                        .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                        .contentType("application/json"))
                .andExpect(status().isOk());

        verify(paymentService).voidPayment(PAYMENT_ID, VALID_IDEMPOTENCY_KEY);
    }

    @Test
    void refundDelegatesAndReturnsResponse() throws Exception {
        when(paymentService.refundPayment(eq(PAYMENT_ID), eq(VALID_IDEMPOTENCY_KEY), any(RefundPaymentRequest.class)))
                .thenReturn(new PaymentDetailResponse(null, null, List.of()));

        mockMvc.perform(post("/api/v1/payments/{paymentId}/refund", PAYMENT_ID)
                        .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                        .contentType("application/json")
                        .content("""
                                {"amount":500.00,"reason":"Customer cancellation refund"}
                                """))
                .andExpect(status().isOk());

        verify(paymentService).refundPayment(eq(PAYMENT_ID), eq(VALID_IDEMPOTENCY_KEY), any(RefundPaymentRequest.class));
    }

    @Test
    void reconciliationDelegatesAndReturnsResponse() throws Exception {
        when(paymentService.reconcilePayment(PAYMENT_ID))
                .thenReturn(new ReconciliationResponse(null, null, null, false));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/payments/{paymentId}/reconciliation", PAYMENT_ID))
                .andExpect(status().isOk());

        verify(paymentService).reconcilePayment(PAYMENT_ID);
    }
}
