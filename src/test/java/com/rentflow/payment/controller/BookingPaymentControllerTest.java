package com.rentflow.payment.controller;

import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.GlobalExceptionHandler;
import com.rentflow.payment.dto.AuthorizePaymentRequest;
import com.rentflow.payment.dto.AuthorizePaymentResponse;
import com.rentflow.payment.dto.PaymentDetailResponse;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingPaymentControllerTest {

    private static final UUID BOOKING_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID BANK_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String VALID_IDEMPOTENCY_KEY = "8b71f8d2-9e1d-4f7a-bbe6-334c3816df91";

    private PaymentService paymentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new BookingPaymentController(paymentService))
                .setControllerAdvice(new GlobalExceptionHandler(new CorrelationIdHelper()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void authorizeMissingIdempotencyKeyReturnsRequiredError() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments/authorize", BOOKING_ID)
                        .contentType("application/json")
                        .content("""
                                {"bankId":"%s","paymentMethod":"BANK_TRANSFER_QR"}
                                """.formatted(BANK_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void authorizeInvalidIdempotencyKeyReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments/authorize", BOOKING_ID)
                        .header("Idempotency-Key", "not-a-v4-uuid")
                        .contentType("application/json")
                        .content("""
                                {"bankId":"%s","paymentMethod":"BANK_TRANSFER_QR"}
                                """.formatted(BANK_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void authorizeDelegatesAndReturnsContractShape() throws Exception {
        UUID paymentId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        AuthorizePaymentResponse response = new AuthorizePaymentResponse(
                new AuthorizePaymentResponse.BookingSummary(
                        BOOKING_ID,
                        BookingStatus.HELD,
                        java.time.LocalDate.of(2026, 6, 1),
                        java.time.LocalDate.of(2026, 6, 3),
                        new BigDecimal("1400000.00"),
                        "VND"),
                new AuthorizePaymentResponse.PaymentSummary(
                        paymentId,
                        PaymentStatus.PENDING_TRANSFER,
                        PaymentMethod.BANK_TRANSFER_QR,
                        PaymentProviderType.VIETQR_MANUAL,
                        "rentflow:booking:" + BOOKING_ID,
                        null,
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "VND",
                        new AuthorizePaymentResponse.TransferInstructionResponse(
                                "VCB",
                                "970436",
                                "1234567890",
                                "RENTFLOW ESCROW",
                                new BigDecimal("1400000.00"),
                                "RENTFLOW " + BOOKING_ID,
                                "manual-vietqr:test")));
        when(paymentService.authorizeBookingPayment(
                eq(BOOKING_ID),
                eq(VALID_IDEMPOTENCY_KEY),
                any(AuthorizePaymentRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments/authorize", BOOKING_ID)
                        .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                        .contentType("application/json")
                        .content("""
                                {"bankId":"%s","paymentMethod":"BANK_TRANSFER_QR"}
                                """.formatted(BANK_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.id").value(BOOKING_ID.toString()))
                .andExpect(jsonPath("$.booking.status").value("HELD"))
                .andExpect(jsonPath("$.booking.totalAmount").value(1400000.00))
                .andExpect(jsonPath("$.payment.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.payment.status").value("PENDING_TRANSFER"))
                .andExpect(jsonPath("$.payment.provider").value("VIETQR_MANUAL"))
                .andExpect(jsonPath("$.payment.transferInstruction.bankCode").value("VCB"));

        verify(paymentService).authorizeBookingPayment(eq(BOOKING_ID), eq(VALID_IDEMPOTENCY_KEY), any(AuthorizePaymentRequest.class));
    }

    @Test
    void simulateTransferConfirmationMissingIdempotencyKeyReturnsRequiredError() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments/simulate-transfer-confirmation", BOOKING_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void simulateTransferConfirmationDelegatesAndReturnsContractShape() throws Exception {
        UUID paymentId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        PaymentDetailResponse response = new PaymentDetailResponse(
                new PaymentDetailResponse.BookingSummary(
                        BOOKING_ID,
                        UUID.fromString("44444444-4444-4444-8444-444444444444"),
                        UUID.fromString("55555555-5555-4555-8555-555555555555"),
                        BookingStatus.PENDING_HOST_APPROVAL,
                        java.time.LocalDate.of(2026, 6, 1),
                        java.time.LocalDate.of(2026, 6, 3)),
                new PaymentDetailResponse.PaymentSummary(
                        paymentId,
                        null,
                        PaymentMethod.BANK_TRANSFER_QR,
                        PaymentProviderType.VIETQR_MANUAL,
                        PaymentStatus.AUTHORIZED,
                        new BigDecimal("1400000.00"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "VND",
                        "rentflow:booking:" + BOOKING_ID,
                        null,
                        null,
                        "SANDBOX_TRANSFER_CONFIRMED",
                        null),
                java.util.List.of());
        when(paymentService.simulateTransferConfirmation(BOOKING_ID, VALID_IDEMPOTENCY_KEY))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments/simulate-transfer-confirmation", BOOKING_ID)
                        .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.id").value(BOOKING_ID.toString()))
                .andExpect(jsonPath("$.payment.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.payment.status").value("AUTHORIZED"));

        verify(paymentService).simulateTransferConfirmation(BOOKING_ID, VALID_IDEMPOTENCY_KEY);
    }
}
