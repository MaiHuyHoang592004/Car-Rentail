package com.rentflow.payment.provider;

import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.payment.entity.PaymentProviderType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentProviderRouter {

    private final List<PaymentProvider> paymentProviders;

    public PaymentProviderRouter(List<PaymentProvider> paymentProviders) {
        this.paymentProviders = paymentProviders;
    }

    public PaymentProvider route(PaymentProviderType providerType) {
        return paymentProviders.stream()
                .filter(provider -> provider.supports(providerType))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "PAYMENT_PROVIDER_UNAVAILABLE",
                        "Payment provider is not available: " + providerType));
    }
}
