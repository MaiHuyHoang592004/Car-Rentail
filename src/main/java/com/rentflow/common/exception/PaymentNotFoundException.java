package com.rentflow.common.exception;

public class PaymentNotFoundException extends ResourceNotFoundException {

    public PaymentNotFoundException(String paymentId) {
        super("PAYMENT_NOT_FOUND", "Payment", paymentId);
    }
}
