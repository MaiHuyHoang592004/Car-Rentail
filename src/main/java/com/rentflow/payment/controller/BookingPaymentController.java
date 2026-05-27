package com.rentflow.payment.controller;

import com.rentflow.common.exception.IdempotencyKeyRequiredException;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.payment.dto.AuthorizePaymentRequest;
import com.rentflow.payment.dto.AuthorizePaymentResponse;
import com.rentflow.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/payments")
@RequiredArgsConstructor
public class BookingPaymentController {

    private static final Pattern UUID_V4_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final PaymentService paymentService;

    @PostMapping("/authorize")
    public ResponseEntity<AuthorizePaymentResponse> authorizePayment(
            @PathVariable UUID bookingId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AuthorizePaymentRequest request) {
        validateIdempotencyKey(idempotencyKey);
        return ResponseEntity.ok(paymentService.authorizeBookingPayment(bookingId, idempotencyKey, request));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequiredException("Idempotency-Key header is required");
        }
        if (!UUID_V4_PATTERN.matcher(idempotencyKey).matches()) {
            throw new ValidationException("Idempotency-Key must be a UUID-v4 value");
        }
    }
}
