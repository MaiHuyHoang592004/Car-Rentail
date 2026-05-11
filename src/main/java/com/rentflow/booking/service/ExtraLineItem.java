package com.rentflow.booking.service;

import com.rentflow.listing.entity.PricingType;

import java.math.BigDecimal;
import java.util.UUID;

public record ExtraLineItem(
        UUID extraId,
        String name,
        PricingType pricingType,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineAmount) {
}
