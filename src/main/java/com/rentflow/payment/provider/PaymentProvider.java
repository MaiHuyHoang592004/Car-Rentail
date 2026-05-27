package com.rentflow.payment.provider;

import com.rentflow.payment.entity.PaymentProviderType;

public interface PaymentProvider {

    boolean supports(PaymentProviderType providerType);

    AuthorizeResult authorize(AuthorizeCommand command);

    CaptureResult capture(CaptureCommand command);

    RefundResult refund(RefundCommand command);

    ProviderOrderSnapshot findByExternalOrderRef(String externalOrderRef);

    VoidResult voidAuthorization(VoidCommand command);
}
