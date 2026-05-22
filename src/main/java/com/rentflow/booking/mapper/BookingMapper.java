package com.rentflow.booking.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.service.BookingResponse;
import com.rentflow.booking.service.BookingSummaryResponse;
import com.rentflow.common.web.PageResponse;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.repository.ListingRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingMapper {

    private final ListingRepository listingRepository;
    private final ObjectMapper objectMapper;

    public BookingSummaryResponse toSummaryResponse(Booking booking) {
        JsonNode priceSnapshot = readTree(booking.getPriceSnapshot());
        return new BookingSummaryResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getListingId(),
                findListingTitle(booking.getListingId()),
                booking.getPickupDate(),
                booking.getReturnDate(),
                booking.getHoldExpiresAt(),
                amountFromSnapshot(priceSnapshot, "totalAmount"),
                textFromSnapshot(priceSnapshot, "currency"),
                booking.getCreatedAt());
    }

    public BookingResponse toResponse(Booking booking) {
        JsonNode priceSnapshot = readTree(booking.getPriceSnapshot());
        JsonNode policySnapshot = readTree(booking.getPolicySnapshot());
        return new BookingResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getListingId(),
                findListingTitle(booking.getListingId()),
                booking.getCustomerId(),
                booking.getHostId(),
                booking.getPickupDate(),
                booking.getReturnDate(),
                booking.getPickupLocation(),
                booking.getReturnLocation(),
                booking.getHoldExpiresAt(),
                amountFromSnapshot(priceSnapshot, "totalAmount"),
                textFromSnapshot(priceSnapshot, "currency"),
                priceSnapshot,
                policySnapshot,
                booking.getCreatedAt());
    }

    public PageResponse<BookingSummaryResponse> toSummaryPage(Page<Booking> page) {
        return PageResponse.from(page, this::toSummaryResponse);
    }

    private String findListingTitle(UUID listingId) {
        Optional<Listing> listing = listingRepository.findById(listingId);
        return listing.map(Listing::getTitle).orElse(null);
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to read booking JSON", e);
        }
    }

    private static BigDecimal amountFromSnapshot(JsonNode snapshot, String field) {
        JsonNode value = snapshot.get(field);
        return value == null || value.isNull() ? null : value.decimalValue();
    }

    private static String textFromSnapshot(JsonNode snapshot, String field) {
        JsonNode value = snapshot.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
