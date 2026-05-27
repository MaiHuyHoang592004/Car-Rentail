package com.rentflow.payment.controller;

import com.rentflow.payment.dto.CapturePaymentRequest;
import com.rentflow.payment.dto.PaymentDetailResponse;
import com.rentflow.payment.dto.ReconciliationResponse;
import com.rentflow.payment.dto.RefundPaymentRequest;
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
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/{paymentId}/capture")
    public ResponseEntity<PaymentDetailResponse> capture(
            @PathVariable UUID paymentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CapturePaymentRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(paymentService.capturePayment(paymentId, idempotencyKey, request));
    }

    @PostMapping("/{paymentId}/void")
    public ResponseEntity<PaymentDetailResponse> voidPayment(
            @PathVariable UUID paymentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(paymentService.voidPayment(paymentId, idempotencyKey));
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<PaymentDetailResponse> refund(
            @PathVariable UUID paymentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RefundPaymentRequest request) {
        IdempotencyKeyValidator.validate(idempotencyKey);
        return ResponseEntity.ok(paymentService.refundPayment(paymentId, idempotencyKey, request));
    }

    @GetMapping("/{paymentId}/reconciliation")
    public ResponseEntity<ReconciliationResponse> reconcile(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.reconcilePayment(paymentId));
    }
}
