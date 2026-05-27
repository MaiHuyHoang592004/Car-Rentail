package com.rentflow.payment.provider.corebank;

import com.rentflow.common.exception.PaymentException;

public class CoreBankAuthorizationFailedException extends PaymentException {

    private final String providerStatus;
    private final String providerErrorCode;
    private final String providerErrorMessage;
    private final String rawResponseJson;

    public CoreBankAuthorizationFailedException(
            String message,
            String providerStatus,
            String providerErrorCode,
            String providerErrorMessage,
            String rawResponseJson) {
        super("PAYMENT_AUTHORIZATION_FAILED", message);
        this.providerStatus = providerStatus;
        this.providerErrorCode = providerErrorCode;
        this.providerErrorMessage = providerErrorMessage;
        this.rawResponseJson = rawResponseJson;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public String getProviderErrorCode() {
        return providerErrorCode;
    }

    public String getProviderErrorMessage() {
        return providerErrorMessage;
    }

    public String getRawResponseJson() {
        return rawResponseJson;
    }
}
