package com.rentflow.booking.service;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.listing.entity.Extra;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.PricingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingPriceCalculatorTest {

    private static final UUID LISTING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_LISTING_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GPS_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CHILD_SEAT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID UNKNOWN_EXTRA_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private BookingPriceCalculator calculator;
    private Listing listing;

    @BeforeEach
    void setUp() {
        calculator = new BookingPriceCalculator();
        listing = listing(LISTING_ID, "700000.00", "VND");
    }

    @Test
    void calculateBaseOnlyTreatsNullExtrasAsEmpty() {
        PriceCalculationResult result = calculator.calculate(
                listing,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                null,
                List.of());

        assertThat(result.rentalDays()).isEqualTo(2);
        assertThat(result.basePricePerDay()).isEqualByComparingTo("700000.00");
        assertThat(result.baseAmount()).isEqualByComparingTo("1400000.00");
        assertThat(result.extraAmount()).isEqualByComparingTo("0");
        assertThat(result.totalAmount()).isEqualByComparingTo("1400000.00");
        assertThat(result.currency()).isEqualTo("VND");
        assertThat(result.extras()).isEmpty();
    }

    @Test
    void calculatePerDayExtra() {
        Extra gps = extra(GPS_ID, listing, "GPS", PricingType.PER_DAY, "50000.00", true);

        PriceCalculationResult result = calculator.calculate(
                listing,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                List.of(new RequestedExtra(GPS_ID, 1)),
                List.of(gps));

        assertThat(result.extraAmount()).isEqualByComparingTo("100000.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("1500000.00");
        assertThat(result.extras()).containsExactly(new ExtraLineItem(
                GPS_ID, "GPS", PricingType.PER_DAY, new BigDecimal("50000.00"), 1, new BigDecimal("100000.00")));
    }

    @Test
    void calculatePerTripExtra() {
        Extra childSeat = extra(CHILD_SEAT_ID, listing, "Child seat", PricingType.PER_TRIP, "80000.00", true);

        PriceCalculationResult result = calculator.calculate(
                listing,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                List.of(new RequestedExtra(CHILD_SEAT_ID, 2)),
                List.of(childSeat));

        assertThat(result.extraAmount()).isEqualByComparingTo("160000.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("1560000.00");
    }

    @Test
    void calculateMixedExtras() {
        Extra gps = extra(GPS_ID, listing, "GPS", PricingType.PER_DAY, "50000.00", true);
        Extra childSeat = extra(CHILD_SEAT_ID, listing, "Child seat", PricingType.PER_TRIP, "80000.00", true);

        PriceCalculationResult result = calculator.calculate(
                listing,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 4),
                List.of(
                        new RequestedExtra(GPS_ID, 1),
                        new RequestedExtra(CHILD_SEAT_ID, 2)),
                List.of(gps, childSeat));

        assertThat(result.rentalDays()).isEqualTo(3);
        assertThat(result.baseAmount()).isEqualByComparingTo("2100000.00");
        assertThat(result.extraAmount()).isEqualByComparingTo("310000.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("2410000.00");
        assertThat(result.extras()).hasSize(2);
    }

    @Test
    void calculateSingleDayBooking() {
        PriceCalculationResult result = calculator.calculate(
                listing,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 2),
                List.of(),
                List.of());

        assertThat(result.rentalDays()).isEqualTo(1);
        assertThat(result.baseAmount()).isEqualByComparingTo("700000.00");
    }

    @Test
    void calculateMaxThirtyDayBooking() {
        PriceCalculationResult result = calculator.calculate(
                listing,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 1),
                List.of(),
                List.of());

        assertThat(result.rentalDays()).isEqualTo(30);
        assertThat(result.baseAmount()).isEqualByComparingTo("21000000.00");
    }

    @Test
    void unknownExtraThrowsValidationError() {
        assertValidationError(() -> calculator.calculate(
                listing,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                List.of(new RequestedExtra(UNKNOWN_EXTRA_ID, 1)),
                List.of()));
    }

    @Test
    void inactiveExtraThrowsValidationError() {
        Extra gps = extra(GPS_ID, listing, "GPS", PricingType.PER_DAY, "50000.00", false);

        assertValidationError(() -> calculator.calculate(
                listing,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                List.of(new RequestedExtra(GPS_ID, 1)),
                List.of(gps)));
    }

    @Test
    void quantityLessThanOneThrowsValidationError() {
        Extra gps = extra(GPS_ID, listing, "GPS", PricingType.PER_DAY, "50000.00", true);

        assertValidationError(() -> calculator.calculate(
                listing,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                List.of(new RequestedExtra(GPS_ID, 0)),
                List.of(gps)));
    }

    @Test
    void extraFromAnotherListingThrowsValidationError() {
        Listing otherListing = listing(OTHER_LISTING_ID, "700000.00", "VND");
        Extra gps = extra(GPS_ID, otherListing, "GPS", PricingType.PER_DAY, "50000.00", true);

        assertValidationError(() -> calculator.calculate(
                listing,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                List.of(new RequestedExtra(GPS_ID, 1)),
                List.of(gps)));
    }

    private Listing listing(UUID id, String basePricePerDay, String currency) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setBasePricePerDay(new BigDecimal(basePricePerDay));
        listing.setCurrency(currency);
        return listing;
    }

    private Extra extra(
            UUID id,
            Listing listing,
            String name,
            PricingType pricingType,
            String price,
            boolean active) {
        Extra extra = new Extra();
        extra.setId(id);
        extra.setListing(listing);
        extra.setName(name);
        extra.setPricingType(pricingType);
        extra.setPrice(new BigDecimal(price));
        extra.setActive(active);
        return extra;
    }

    private void assertValidationError(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }
}
