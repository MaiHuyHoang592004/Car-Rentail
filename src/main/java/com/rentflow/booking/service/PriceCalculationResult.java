package com.rentflow.booking.service;

import java.math.BigDecimal;
import java.util.List;

public record PriceCalculationResult(
        long rentalDays,
        BigDecimal basePricePerDay,
        BigDecimal baseAmount,
        BigDecimal extraAmount,
        BigDecimal totalAmount,
        String currency,
        List<ExtraLineItem> extras) {
}
