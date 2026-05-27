package com.rentflow.payment.provider;

public record RefundResult(
        String providerStatus,
        String providerJournalId,
        String providerMetadataJson
) {
}
