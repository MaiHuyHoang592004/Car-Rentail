package com.rentflow.report.service;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.report.dto.EarningsReportResponse;
import com.rentflow.report.dto.RevenueReportResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class ReportService {

    private final BookingPaymentRepository bookingPaymentRepository;
    private final ReportProperties reportProperties;
    private final SecurityContext securityContext;

    public ReportService(
            BookingPaymentRepository bookingPaymentRepository,
            ReportProperties reportProperties,
            SecurityContext securityContext) {
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.reportProperties = reportProperties;
        this.securityContext = securityContext;
    }

    public RevenueReportResponse revenue(LocalDate from, LocalDate to) {
        DateRange range = normalizeRange(from, to);
        BigDecimal captured = normalizeMoney(
                bookingPaymentRepository.sumCapturedAmountInRange(range.fromInclusive(), range.toExclusive()));
        long count = bookingPaymentRepository.countCapturedBookingsInRange(range.fromInclusive(), range.toExclusive());
        BigDecimal fee = calculateFee(captured);
        BigDecimal net = normalizeMoney(captured.subtract(fee));
        return new RevenueReportResponse(range.from(), range.to(), captured, fee, net, count);
    }

    public EarningsReportResponse hostEarnings(LocalDate from, LocalDate to) {
        DateRange range = normalizeRange(from, to);
        UUID hostId = securityContext.currentUserId();
        BigDecimal captured = normalizeMoney(
                bookingPaymentRepository.sumCapturedAmountForHostInRange(hostId, range.fromInclusive(), range.toExclusive()));
        long count = bookingPaymentRepository.countCapturedBookingsForHostInRange(hostId, range.fromInclusive(), range.toExclusive());
        BigDecimal fee = calculateFee(captured);
        BigDecimal net = normalizeMoney(captured.subtract(fee));
        return new EarningsReportResponse(range.from(), range.to(), hostId, captured, fee, net, count);
    }

    private DateRange normalizeRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ValidationException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new ValidationException("to must be on or after from");
        }
        Instant fromInclusive = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toExclusive = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new DateRange(from, to, fromInclusive, toExclusive);
    }

    private BigDecimal calculateFee(BigDecimal gross) {
        return normalizeMoney(gross.multiply(reportProperties.getPlatformFeeRate()));
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return safe.setScale(2, RoundingMode.HALF_UP);
    }

    private record DateRange(LocalDate from, LocalDate to, Instant fromInclusive, Instant toExclusive) {
    }
}
