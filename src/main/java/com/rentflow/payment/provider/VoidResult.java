package com.rentflow.payment.provider;

public record VoidResult(
        String providerStatus,
        String providerMetadataJson
) {
}
