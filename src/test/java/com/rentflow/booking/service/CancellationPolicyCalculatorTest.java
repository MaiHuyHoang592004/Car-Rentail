package com.rentflow.booking.service;

import com.rentflow.listing.entity.CancellationPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationPolicyCalculatorTest {

    private static final ZoneOffset UTC = ZoneOffset.UTC;
    private static final BigDecimal TOTAL = new BigDecimal("1000000.00");

    private CancellationPolicyCalculator calcAt(String isoInstant) {
        Clock clock = Clock.fixed(Instant.parse(isoInstant), UTC);
        return new CancellationPolicyCalculator(clock);
    }

    @Test
    void flexible_exact24HoursBoundary_fullRefund() {
        CancellationPolicyCalculator calc = calcAt("2026-05-11T00:00:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 12);

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.FLEXIBLE, pickup, TOTAL);

        assertThat(result.refundPercent()).isEqualByComparingTo("1.00");
        assertThat(result.penaltyPercent()).isEqualByComparingTo("0.00");
        assertThat(result.penaltyAmount()).isEqualByComparingTo("0.00");
        assertThat(result.refundAmount()).isEqualByComparingTo("1000000.00");
    }

    @Test
    void flexible_justUnder24Hours_80pctRefund() {
        CancellationPolicyCalculator calc = calcAt("2026-05-11T00:01:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 12);

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.FLEXIBLE, pickup, TOTAL);

        assertThat(result.refundPercent()).isEqualByComparingTo("0.80");
        assertThat(result.penaltyPercent()).isEqualByComparingTo("0.20");
        assertThat(result.penaltyAmount()).isEqualByComparingTo("200000.00");
        assertThat(result.refundAmount()).isEqualByComparingTo("800000.00");
    }

    @Test
    void flexible_fullRefund_whenHoursUntilPickupExceeds24() {
        CancellationPolicyCalculator calc = calcAt("2026-05-09T00:00:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 12);

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.FLEXIBLE, pickup, TOTAL);

        assertThat(result.refundPercent()).isEqualByComparingTo("1.00");
    }

    @Test
    void flexible_80pctRefund_whenHoursUntilPickupBelow24() {
        CancellationPolicyCalculator calc = calcAt("2026-05-10T00:01:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 11);

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.FLEXIBLE, pickup, TOTAL);

        assertThat(result.refundPercent()).isEqualByComparingTo("0.80");
    }

    @Test
    void moderate_exact72HoursBoundary_fullRefund() {
        CancellationPolicyCalculator calc = calcAt("2026-05-08T00:00:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 11);

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.MODERATE, pickup, TOTAL);

        assertThat(result.refundPercent()).isEqualByComparingTo("1.00");
        assertThat(result.penaltyPercent()).isEqualByComparingTo("0.00");
        assertThat(result.penaltyAmount()).isEqualByComparingTo("0.00");
        assertThat(result.refundAmount()).isEqualByComparingTo("1000000.00");
    }

    @Test
    void moderate_justUnder72Hours_50pctRefund() {
        CancellationPolicyCalculator calc = calcAt("2026-05-08T00:01:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 11);

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.MODERATE, pickup, TOTAL);

        assertThat(result.refundPercent()).isEqualByComparingTo("0.50");
        assertThat(result.penaltyPercent()).isEqualByComparingTo("0.50");
        assertThat(result.penaltyAmount()).isEqualByComparingTo("500000.00");
        assertThat(result.refundAmount()).isEqualByComparingTo("500000.00");
    }

    @Test
    void moderate_fullRefund_whenHoursUntilPickupAbove72() {
        CancellationPolicyCalculator calc = calcAt("2026-05-06T00:00:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 11);

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.MODERATE, pickup, TOTAL);

        assertThat(result.refundPercent()).isEqualByComparingTo("1.00");
    }

    @Test
    void moderate_50pctRefund_whenHoursUntilPickupBetween24And72() {
        CancellationPolicyCalculator calc = calcAt("2026-05-08T12:00:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 11);

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.MODERATE, pickup, TOTAL);

        assertThat(result.refundPercent()).isEqualByComparingTo("0.50");
    }

    @Test
    void moderate_exact24HoursBoundary_50pctRefund() {
        CancellationPolicyCalculator calc = calcAt("2026-05-10T00:00:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 11);

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.MODERATE, pickup, TOTAL);

        assertThat(result.refundPercent()).isEqualByComparingTo("0.50");
    }

    @Test
    void moderate_justUnder24Hours_noRefund() {
        CancellationPolicyCalculator calc = calcAt("2026-05-10T00:01:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 11);

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.MODERATE, pickup, TOTAL);

        assertThat(result.refundPercent()).isEqualByComparingTo("0.00");
        assertThat(result.penaltyPercent()).isEqualByComparingTo("1.00");
        assertThat(result.penaltyAmount()).isEqualByComparingTo("1000000.00");
        assertThat(result.refundAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void moderate_noRefund_whenHoursUntilPickupBelow24() {
        CancellationPolicyCalculator calc = calcAt("2026-05-10T12:01:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 11);

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.MODERATE, pickup, TOTAL);

        assertThat(result.refundPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void strict_exact168HoursBoundary_fullRefund() {
        CancellationPolicyCalculator calc = calcAt("2026-05-04T00:00:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 11);

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.STRICT, pickup, TOTAL);

        assertThat(result.refundPercent()).isEqualByComparingTo("1.00");
        assertThat(result.penaltyPercent()).isEqualByComparingTo("0.00");
        assertThat(result.penaltyAmount()).isEqualByComparingTo("0.00");
        assertThat(result.refundAmount()).isEqualByComparingTo("1000000.00");
    }

    @Test
    void strict_justUnder168Hours_noRefund() {
        CancellationPolicyCalculator calc = calcAt("2026-05-04T00:01:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 11);

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.STRICT, pickup, TOTAL);

        assertThat(result.refundPercent()).isEqualByComparingTo("0.00");
        assertThat(result.penaltyPercent()).isEqualByComparingTo("1.00");
        assertThat(result.penaltyAmount()).isEqualByComparingTo("1000000.00");
        assertThat(result.refundAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void strict_fullRefund_whenHoursUntilPickupAbove168() {
        CancellationPolicyCalculator calc = calcAt("2026-05-01T00:00:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 11);

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.STRICT, pickup, TOTAL);

        assertThat(result.refundPercent()).isEqualByComparingTo("1.00");
    }

    @Test
    void strict_noRefund_whenHoursUntilPickupBelow168() {
        CancellationPolicyCalculator calc = calcAt("2026-05-05T00:00:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 11);

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.STRICT, pickup, TOTAL);

        assertThat(result.refundPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void allPolicies_refundAmountPlusPenaltyAmountEqualsTotal() {
        CancellationPolicyCalculator calc = calcAt("2026-05-10T00:01:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 11);
        BigDecimal total = new BigDecimal("1000000.00");

        for (CancellationPolicy policy : CancellationPolicy.values()) {
            CancellationPolicyCalculator.CancellationPolicyResult result =
                    calc.calculate(policy, pickup, total);

            BigDecimal sum = result.refundAmount().add(result.penaltyAmount());
            assertThat(sum).isEqualByComparingTo(total);
        }
    }

    @Test
    void penaltyAmount_roundsCorrectly_halfUp() {
        CancellationPolicyCalculator calc = calcAt("2026-05-10T00:01:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 11);
        BigDecimal oddTotal = new BigDecimal("333333.33");

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.FLEXIBLE, pickup, oddTotal);

        assertThat(result.refundAmount().add(result.penaltyAmount()))
                .isEqualByComparingTo(oddTotal);
    }

    @Test
    void penaltyPercent_isOneMinusRefundPercent() {
        CancellationPolicyCalculator calc = calcAt("2026-05-08T12:00:00Z");
        LocalDate pickup = LocalDate.of(2026, 5, 11);

        CancellationPolicyCalculator.CancellationPolicyResult result =
                calc.calculate(CancellationPolicy.MODERATE, pickup, TOTAL);

        BigDecimal expectedPenalty = BigDecimal.ONE.subtract(result.refundPercent());
        assertThat(result.penaltyPercent()).isEqualByComparingTo(expectedPenalty);
    }
}
