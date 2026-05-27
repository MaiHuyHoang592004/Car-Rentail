package com.rentflow.common.exception;

public class PaymentProviderUnavailableException extends RentFlowException {

    public PaymentProviderUnavailableException(String message) {
        super("PAYMENT_PROVIDER_UNAVAILABLE", message);
    }

    public PaymentProviderUnavailableException(String message, Throwable cause) {
        super("PAYMENT_PROVIDER_UNAVAILABLE", message, cause);
    }
}
