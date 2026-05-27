package com.rentflow.payment.controller;

import com.rentflow.payment.dto.PaymentBankListResponse;
import com.rentflow.payment.service.PaymentBankService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment-banks")
@RequiredArgsConstructor
public class PaymentBankController {

    private final PaymentBankService paymentBankService;

    @GetMapping
    public ResponseEntity<PaymentBankListResponse> listActiveBanks() {
        return ResponseEntity.ok(paymentBankService.listActiveBanks());
    }
}
