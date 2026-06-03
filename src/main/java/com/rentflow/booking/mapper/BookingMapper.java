package com.rentflow.booking.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.service.CancellationPolicyCalculator;
import com.rentflow.booking.service.CancellationPreviewResponse;
import com.rentflow.booking.service.BookingResponse;
import com.rentflow.booking.service.BookingSummaryResponse;
import com.rentflow.common.web.PageResponse;
import com.rentflow.dispute.repository.DisputeRepository;
import com.rentflow.listing.entity.CancellationPolicy;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.review.repository.ReviewRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {

    private final ListingRepository listingRepository;
    private final BookingPaymentRepository bookingPaymentRepository;
    private final ReviewRepository reviewRepository;
    private final DisputeRepository disputeRepository;
    private final CancellationPolicyCalculator cancellationPolicyCalculator;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Autowired
    public BookingMapper(
            ListingRepository listingRepository,
            BookingPaymentRepository bookingPaymentRepository,
            ReviewRepository reviewRepository,
            DisputeRepository disputeRepository,
            CancellationPolicyCalculator cancellationPolicyCalculator,
            Clock clock,
            ObjectMapper objectMapper) {
        this.listingRepository = listingRepository;
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.reviewRepository = reviewRepository;
        this.disputeRepository = disputeRepository;
        this.cancellationPolicyCalculator = cancellationPolicyCalculator;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public BookingSummaryResponse toSummaryResponse(Booking booking) {
        JsonNode priceSnapshot = readTree(booking.getPriceSnapshot());
        Optional<BookingPayment> payment = bookingPaymentRepository.findByBookingId(booking.getId());
        return new BookingSummaryResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getListingId(),
                findListingTitle(booking.getListingId()),
                booking.getPickupDate(),
                booking.getReturnDate(),
                booking.getHoldExpiresAt(),
                booking.getHostApprovalExpiresAt(),
                amountFromSnapshot(priceSnapshot, "totalAmount"),
                textFromSnapshot(priceSnapshot, "currency"),
                booking.getCreatedAt(),
                payment.map(BookingPayment::isVoidRetryRequired).orElse(false),
                payment.filter(BookingPayment::isVoidRetryRequired)
                        .map(BookingPayment::getProviderStatus)
                        .orElseGet(() -> retryExhausted(payment) ? "VOID_RETRY_EXHAUSTED" : null),
                payment.map(item -> item.getStatus().name()).orElse(null),
                payment.map(BookingPayment::getVoidRetryLastError).orElse(null),
                payment.map(BookingPayment::getVoidRetryCount).orElse(0));
    }

    public BookingResponse toResponse(Booking booking) {
        JsonNode priceSnapshot = readTree(booking.getPriceSnapshot());
        JsonNode policySnapshot = readTree(booking.getPolicySnapshot());
        Optional<BookingPayment> payment = bookingPaymentRepository.findByBookingId(booking.getId());
        boolean reviewSubmitted = reviewRepository != null
                && reviewRepository.existsByBookingIdAndReviewerId(booking.getId(), booking.getCustomerId());
        boolean disputeSubmitted = disputeRepository != null
                && disputeRepository.existsByBookingIdAndCustomerId(booking.getId(), booking.getCustomerId());
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
                booking.getHostApprovalExpiresAt(),
                amountFromSnapshot(priceSnapshot, "totalAmount"),
                textFromSnapshot(priceSnapshot, "currency"),
                priceSnapshot,
                policySnapshot,
                booking.getRejectionReason(),
                booking.getCreatedAt(),
                payment.map(BookingPayment::isVoidRetryRequired).orElse(false),
                payment.filter(BookingPayment::isVoidRetryRequired)
                        .map(BookingPayment::getProviderStatus)
                        .orElseGet(() -> retryExhausted(payment) ? "VOID_RETRY_EXHAUSTED" : null),
                payment.map(item -> item.getStatus().name()).orElse(null),
                payment.map(BookingPayment::getVoidRetryLastError).orElse(null),
                payment.map(BookingPayment::getVoidRetryCount).orElse(0),
                cancellationPreview(booking, priceSnapshot, policySnapshot),
                booking.getStatus() == BookingStatus.COMPLETED && !reviewSubmitted,
                reviewSubmitted,
                booking.getStatus() == BookingStatus.COMPLETED && !disputeSubmitted,
                disputeSubmitted);
    }

    public PageResponse<BookingSummaryResponse> toSummaryPage(Page<Booking> page) {
        return PageResponse.from(page, this::toSummaryResponse);
    }

    private boolean retryExhausted(Optional<BookingPayment> payment) {
        return payment
                .filter(item -> !item.isVoidRetryRequired())
                .filter(item -> item.getVoidRetryCount() != null && item.getVoidRetryCount() > 0)
                .filter(item -> item.getVoidRetryLastError() != null && !item.getVoidRetryLastError().isBlank())
                .filter(item -> item.getStatus() == com.rentflow.payment.entity.PaymentStatus.AUTHORIZED
                        || item.getStatus() == com.rentflow.payment.entity.PaymentStatus.CAPTURED)
                .isPresent();
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

    private CancellationPreviewResponse cancellationPreview(
            Booking booking,
            JsonNode priceSnapshot,
            JsonNode policySnapshot) {
        BigDecimal totalAmount = amountFromSnapshot(priceSnapshot, "totalAmount");
        String currency = textFromSnapshot(priceSnapshot, "currency");
        CancellationPolicy policy = policyFromSnapshot(policySnapshot);
        if (totalAmount == null || currency == null || policy == null) {
            return new CancellationPreviewResponse(false, BigDecimal.ZERO, BigDecimal.ZERO, currency, policy);
        }
        if (booking.getStatus() == BookingStatus.HELD || booking.getStatus() == BookingStatus.PENDING_HOST_APPROVAL) {
            return new CancellationPreviewResponse(true, totalAmount, BigDecimal.ZERO, currency, policy);
        }
        if (booking.getStatus() == BookingStatus.CONFIRMED
                && booking.getPickupDate().isAfter(LocalDate.now(clock))) {
            CancellationPolicyCalculator.CancellationPolicyResult result =
                    cancellationPolicyCalculator.calculate(policy, booking.getPickupDate(), totalAmount);
            return new CancellationPreviewResponse(true, result.refundAmount(), result.penaltyAmount(), currency, policy);
        }
        return new CancellationPreviewResponse(false, BigDecimal.ZERO, BigDecimal.ZERO, currency, policy);
    }

    private CancellationPolicy policyFromSnapshot(JsonNode policySnapshot) {
        JsonNode value = policySnapshot.get("cancellationPolicy");
        if (value == null || value.isNull()) {
            return null;
        }
        return CancellationPolicy.valueOf(value.asText());
    }
}
