package com.rentflow.booking.service;

import com.rentflow.listing.entity.CancellationPolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class CancellationPolicyCalculator {

    private final Clock clock;

    public CancellationPolicyCalculator(Clock clock) {
        this.clock = clock;
    }

    public CancellationPolicyResult calculate(
            CancellationPolicy policy,
            LocalDate pickupDate,
            BigDecimal totalAmount) {
        Instant now = Instant.now(clock);
        Instant pickupStart = pickupDate.atStartOfDay(clock.getZone()).toInstant();
        long hoursUntilPickup = ChronoUnit.HOURS.between(now, pickupStart);

        BigDecimal refundPercent = switch (policy) {
            case FLEXIBLE -> hoursUntilPickup >= 24 ? new BigDecimal("1.00") : new BigDecimal("0.80");
            case MODERATE -> {
                if (hoursUntilPickup >= 72) {
                    yield new BigDecimal("1.00");
                }
                if (hoursUntilPickup >= 24) {
                    yield new BigDecimal("0.50");
                }
                yield BigDecimal.ZERO;
            }
            case STRICT -> hoursUntilPickup >= 168 ? new BigDecimal("1.00") : BigDecimal.ZERO;
        };

        BigDecimal penaltyPercent = BigDecimal.ONE.subtract(refundPercent);
        BigDecimal penaltyAmount = totalAmount.multiply(penaltyPercent).setScale(2, RoundingMode.HALF_UP);
        BigDecimal refundAmount = totalAmount.subtract(penaltyAmount).setScale(2, RoundingMode.HALF_UP);

        return new CancellationPolicyResult(refundPercent, penaltyPercent, penaltyAmount, refundAmount);
    }

    public record CancellationPolicyResult(
            BigDecimal refundPercent,
            BigDecimal penaltyPercent,
            BigDecimal penaltyAmount,
            BigDecimal refundAmount
    ) {
    }
}
