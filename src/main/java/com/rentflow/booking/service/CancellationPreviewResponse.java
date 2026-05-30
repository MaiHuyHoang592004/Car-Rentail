package com.rentflow.booking.service;

import com.rentflow.listing.entity.CancellationPolicy;

import java.math.BigDecimal;

public record CancellationPreviewResponse(
        boolean eligible,
        BigDecimal refundableAmount,
        BigDecimal penaltyAmount,
        String currency,
        CancellationPolicy policy) {
}
