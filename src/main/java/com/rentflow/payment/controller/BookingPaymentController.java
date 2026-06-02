package com.rentflow.payment.controller;

import com.rentflow.payment.dto.AuthorizePaymentRequest;
import com.rentflow.payment.dto.AuthorizePaymentResponse;
import com.rentflow.payment.dto.PaymentDetailResponse;
import com.rentflow.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/payments")
@RequiredArgsConstructor
public class BookingPaymentController {

    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<PaymentDetailResponse> getPayment(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(paymentService.getBookingPayment(bookingId));
    }

    @PostMapping("/authorize")
    public ResponseEntity<AuthorizePaymentResponse> authorizePayment(
            @PathVariable UUID bookingId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AuthorizePaymentRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(paymentService.authorizeBookingPayment(bookingId, idempotencyKey, request));
    }

    @PostMapping("/simulate-transfer-confirmation")
    public ResponseEntity<PaymentDetailResponse> simulateTransferConfirmation(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(paymentService.simulateTransferConfirmation(bookingId));
    }
}
