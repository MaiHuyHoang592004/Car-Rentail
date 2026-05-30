package com.rentflow.report.service;

import com.rentflow.common.security.SecurityContext;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.report.dto.EarningsReportResponse;
import com.rentflow.report.dto.RevenueReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private BookingPaymentRepository bookingPaymentRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private AvailabilityCalendarRepository availabilityRepository;
    @Mock private SecurityContext securityContext;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        ReportProperties properties = new ReportProperties();
        properties.setPlatformFeeRate(new BigDecimal("0.15"));
        reportService = new ReportService(
                bookingPaymentRepository,
                bookingRepository,
                listingRepository,
                availabilityRepository,
                properties,
                securityContext);
    }

    @Test
    void revenueCalculatesFeeAndNet() {
        when(bookingPaymentRepository.sumCapturedAmountInRange(any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("1000000.00"));
        when(bookingPaymentRepository.countCapturedBookingsInRange(any(Instant.class), any(Instant.class)))
                .thenReturn(3L);

        RevenueReportResponse response = reportService.revenue(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertThat(response.totalCaptured()).isEqualByComparingTo("1000000.00");
        assertThat(response.platformFeeAmount()).isEqualByComparingTo("150000.00");
        assertThat(response.netRevenue()).isEqualByComparingTo("850000.00");
        assertThat(response.bookingCount()).isEqualTo(3);
    }

    @Test
    void hostEarningsUsesCurrentHostId() {
        UUID hostId = UUID.randomUUID();
        when(securityContext.currentUserId()).thenReturn(hostId);
        when(bookingPaymentRepository.sumCapturedAmountForHostInRange(eq(hostId), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("2000000.00"));
        when(bookingPaymentRepository.countCapturedBookingsForHostInRange(eq(hostId), any(Instant.class), any(Instant.class)))
                .thenReturn(5L);

        EarningsReportResponse response = reportService.hostEarnings(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertThat(response.hostId()).isEqualTo(hostId);
        assertThat(response.grossCaptured()).isEqualByComparingTo("2000000.00");
        assertThat(response.platformFeeAmount()).isEqualByComparingTo("300000.00");
        assertThat(response.netEarnings()).isEqualByComparingTo("1700000.00");
        assertThat(response.bookingCount()).isEqualTo(5);
    }
}
