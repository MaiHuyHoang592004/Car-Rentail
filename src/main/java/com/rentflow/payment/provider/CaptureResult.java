package com.rentflow.payment.provider;

public record CaptureResult(
        String providerStatus,
        String providerJournalId,
        String providerMetadataJson
) {
}
