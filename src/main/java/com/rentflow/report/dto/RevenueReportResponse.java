package com.rentflow.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueReportResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal totalCaptured,
        BigDecimal platformFeeAmount,
        BigDecimal netRevenue,
        long bookingCount) {
}
