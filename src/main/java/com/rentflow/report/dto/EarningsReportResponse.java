package com.rentflow.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EarningsReportResponse(
        LocalDate from,
        LocalDate to,
        UUID hostId,
        BigDecimal grossCaptured,
        BigDecimal platformFeeAmount,
        BigDecimal netEarnings,
        long bookingCount) {
}
