package com.rentflow.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record HostOverviewReportResponse(
        LocalDate from,
        LocalDate to,
        UUID hostId,
        BigDecimal grossCaptured,
        BigDecimal netEarnings,
        long bookingCount,
        long activeListings,
        long pendingApprovalListings,
        long blockedDays,
        long holdDays,
        long bookedDays,
        long generatedDays,
        BigDecimal occupancyRate,
        BigDecimal blockedRate
) {
}
