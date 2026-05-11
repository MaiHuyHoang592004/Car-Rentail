package com.rentflow.booking.service;

import com.rentflow.listing.entity.CancellationPolicy;

public record PolicySnapshot(
        CancellationPolicy cancellationPolicy,
        Boolean instantBook,
        Integer dailyKmLimit) {
}
