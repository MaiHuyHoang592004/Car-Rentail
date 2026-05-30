package com.rentflow.dispute.dto;

import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingTimelineEntry;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdminDisputeDetailResponse(
        DisputeResponse dispute,
        BookingContext booking,
        PaymentContext payment,
        List<TimelineEntry> timeline) {

    public record BookingContext(
            UUID id,
            BookingStatus status,
            UUID listingId,
            UUID customerId,
            UUID hostId,
            LocalDate pickupDate,
            LocalDate returnDate) {

        public static BookingContext from(Booking booking) {
            return new BookingContext(
                    booking.getId(),
                    booking.getStatus(),
                    booking.getListingId(),
                    booking.getCustomerId(),
                    booking.getHostId(),
                    booking.getPickupDate(),
                    booking.getReturnDate());
        }
    }

    public record PaymentContext(
            UUID id,
            PaymentStatus status,
            PaymentProviderType provider,
            BigDecimal authorizedAmount,
            BigDecimal capturedAmount,
            BigDecimal refundedAmount,
            String currency,
            boolean voidRetryRequired,
            String providerStatus) {

        public static PaymentContext from(BookingPayment payment) {
            if (payment == null) {
                return null;
            }
            return new PaymentContext(
                    payment.getId(),
                    payment.getStatus(),
                    payment.getProvider(),
                    payment.getAuthorizedAmount(),
                    payment.getCapturedAmount(),
                    payment.getRefundedAmount(),
                    payment.getCurrency(),
                    payment.isVoidRetryRequired(),
                    payment.getProviderStatus());
        }
    }

    public record TimelineEntry(
            UUID id,
            String eventType,
            UUID actorUserId,
            String actorType,
            String payload,
            Instant createdAt) {

        public static TimelineEntry from(BookingTimelineEntry entry) {
            return new TimelineEntry(
                    entry.getId(),
                    entry.getEventType(),
                    entry.getActorUserId(),
                    entry.getActorType(),
                    entry.getPayload(),
                    entry.getCreatedAt());
        }
    }
}
