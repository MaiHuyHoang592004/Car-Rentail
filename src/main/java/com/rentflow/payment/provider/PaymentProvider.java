package com.rentflow.payment.provider;

import com.rentflow.payment.entity.PaymentProviderType;

public interface PaymentProvider {

    boolean supports(PaymentProviderType providerType);

    AuthorizeResult authorize(AuthorizeCommand command);

    default VoidResult voidAuthorization(VoidCommand command) {
        throw new UnsupportedOperationException("Void is not supported by provider " + getClass().getSimpleName());
    }
}
