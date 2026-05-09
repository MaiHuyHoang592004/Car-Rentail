package com.rentflow.common.exception;

public class PaymentException extends RentFlowException {

    public PaymentException(String code, String message) {
        super(code, message);
    }

    public PaymentException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
