package com.rentflow.booking.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.service.BookingResponse;
import com.rentflow.booking.service.BookingSummaryResponse;
import com.rentflow.booking.service.CancellationPolicyCalculator;
import com.rentflow.common.web.PageResponse;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.repository.BookingPaymentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class BookingMapperTest {

    @Mock private ListingRepository listingRepository;
    @Mock private BookingPaymentRepository bookingPaymentRepository;
    private BookingMapper mapper;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-23T00:00:00Z"), ZoneOffset.UTC);
        mapper = new BookingMapper(
                listingRepository,
                bookingPaymentRepository,
                null,
                null,
                new CancellationPolicyCalculator(clock),
                clock,
                new ObjectMapper());
    }

    @Test
    void toResponse_extractsPriceFieldsFromSnapshot() {
        Booking booking = sampleBooking();
        when(listingRepository.findById(any())).thenReturn(Optional.empty());

        BookingResponse r = mapper.toResponse(booking);

        assertThat(r.id()).isEqualTo(booking.getId());
        assertThat(r.totalAmount()).isEqualByComparingTo("1500000.00");
        assertThat(r.currency()).isEqualTo("VND");
        assertThat(r.priceSnapshot()).isNotNull();
        assertThat(r.policySnapshot()).isNotNull();
    }

    @Test
    void toSummaryResponse_resolvesListingTitle() {
        Booking booking = sampleBooking();
        Listing listing = new Listing();
        listing.setTitle("Toyota Vios 2022");
        when(listingRepository.findById(booking.getListingId())).thenReturn(Optional.of(listing));

        BookingSummaryResponse r = mapper.toSummaryResponse(booking);

        assertThat(r.listingTitle()).isEqualTo("Toyota Vios 2022");
        assertThat(r.totalAmount()).isEqualByComparingTo("1500000.00");
    }

    @Test
    void toSummaryResponse_includesRetryMetadataWhenPaymentRequiresVoidRetry() {
        Booking booking = sampleBooking();
        BookingPayment payment = new BookingPayment();
        payment.setBookingId(booking.getId());
        payment.setVoidRetryRequired(true);
        payment.setProviderStatus("VOID_RETRY_REQUIRED");
        when(listingRepository.findById(any())).thenReturn(Optional.empty());
        when(bookingPaymentRepository.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));

        BookingSummaryResponse r = mapper.toSummaryResponse(booking);

        assertThat(r.voidRetryRequired()).isTrue();
        assertThat(r.paymentRetryState()).isEqualTo("VOID_RETRY_REQUIRED");
    }

    @Test
    void toSummaryResponse_nullTitleWhenListingMissing() {
        Booking booking = sampleBooking();
        when(listingRepository.findById(any())).thenReturn(Optional.empty());

        BookingSummaryResponse r = mapper.toSummaryResponse(booking);

        assertThat(r.listingTitle()).isNull();
    }

    @Test
    void toSummaryPage_preservesPagingMetadata() {
        Booking booking = sampleBooking();
        when(listingRepository.findById(any())).thenReturn(Optional.empty());
        org.springframework.data.domain.Page<Booking> page = new PageImpl<>(
                List.of(booking), PageRequest.of(2, 10), 47);

        PageResponse<BookingSummaryResponse> r = mapper.toSummaryPage(page);

        assertThat(r.content()).hasSize(1);
        assertThat(r.page()).isEqualTo(2);
        assertThat(r.size()).isEqualTo(10);
        assertThat(r.totalElements()).isEqualTo(47);
        assertThat(r.totalPages()).isEqualTo(5);
    }

    private Booking sampleBooking() {
        Booking b = new Booking();
        b.setId(UUID.randomUUID());
        b.setStatus(BookingStatus.HELD);
        b.setListingId(UUID.randomUUID());
        b.setCustomerId(UUID.randomUUID());
        b.setHostId(UUID.randomUUID());
        b.setPickupDate(LocalDate.of(2026, 5, 25));
        b.setReturnDate(LocalDate.of(2026, 5, 27));
        b.setPickupLocation("Hanoi");
        b.setReturnLocation("Hanoi");
        b.setHoldExpiresAt(Instant.parse("2026-05-25T00:15:00Z"));
        b.setPriceSnapshot("{\"totalAmount\":1500000.00,\"currency\":\"VND\"}");
        b.setPolicySnapshot("{\"cancellationPolicy\":\"FLEXIBLE\"}");
        b.setCreatedAt(Instant.parse("2026-05-23T00:00:00Z"));
        return b;
    }

    @SuppressWarnings("unused")
    private static BigDecimal bd(String s) { return new BigDecimal(s); }
}
