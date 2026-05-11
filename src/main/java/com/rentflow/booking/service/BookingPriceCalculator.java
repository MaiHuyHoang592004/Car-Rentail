package com.rentflow.booking.service;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.listing.entity.Extra;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.PricingType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class BookingPriceCalculator {

    public PriceCalculationResult calculate(
            Listing listing,
            LocalDate pickupDate,
            LocalDate returnDate,
            List<RequestedExtra> requestedExtras,
            List<Extra> availableExtras) {
        long rentalDays = ChronoUnit.DAYS.between(pickupDate, returnDate);
        BigDecimal baseAmount = listing.getBasePricePerDay().multiply(BigDecimal.valueOf(rentalDays));
        List<RequestedExtra> normalizedRequests = requestedExtras == null ? List.of() : requestedExtras;
        Map<UUID, Extra> extrasById = (availableExtras == null ? List.<Extra>of() : availableExtras).stream()
                .collect(Collectors.toMap(Extra::getId, Function.identity(), (first, ignored) -> first));

        List<ExtraLineItem> lineItems = new ArrayList<>();
        BigDecimal extraAmount = BigDecimal.ZERO;

        for (RequestedExtra request : normalizedRequests) {
            validateQuantity(request);
            Extra extra = extrasById.get(request.extraId());
            validateExtra(listing, extra, request.extraId());

            BigDecimal lineAmount = calculateLineAmount(extra, request.quantity(), rentalDays);
            extraAmount = extraAmount.add(lineAmount);
            lineItems.add(new ExtraLineItem(
                    extra.getId(),
                    extra.getName(),
                    extra.getPricingType(),
                    extra.getPrice(),
                    request.quantity(),
                    lineAmount));
        }

        return new PriceCalculationResult(
                rentalDays,
                listing.getBasePricePerDay(),
                baseAmount,
                extraAmount,
                baseAmount.add(extraAmount),
                listing.getCurrency(),
                List.copyOf(lineItems));
    }

    private BigDecimal calculateLineAmount(Extra extra, int quantity, long rentalDays) {
        BigDecimal quantityAmount = extra.getPrice().multiply(BigDecimal.valueOf(quantity));
        if (extra.getPricingType() == PricingType.PER_DAY) {
            return quantityAmount.multiply(BigDecimal.valueOf(rentalDays));
        }
        return quantityAmount;
    }

    private void validateQuantity(RequestedExtra request) {
        if (request.quantity() < 1) {
            throw validation("Extra quantity must be greater than zero");
        }
    }

    private void validateExtra(Listing listing, Extra extra, UUID requestedExtraId) {
        if (extra == null) {
            throw validation("Unknown extra id: " + requestedExtraId);
        }
        if (extra.getListing() == null || !listing.getId().equals(extra.getListing().getId())) {
            throw validation("Extra does not belong to listing: " + requestedExtraId);
        }
        if (!Boolean.TRUE.equals(extra.getActive())) {
            throw validation("Extra is inactive: " + requestedExtraId);
        }
    }

    private ValidationException validation(String message) {
        return new ValidationException(message);
    }
}
