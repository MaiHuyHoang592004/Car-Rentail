package com.rentflow.scheduler;

import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.booking.service.AvailabilityReserver;
import com.rentflow.common.exception.PaymentProviderUnavailableException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.provider.PaymentProvider;
import com.rentflow.payment.provider.PaymentProviderRouter;
import com.rentflow.payment.provider.VoidResult;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpireHostApprovalsProcessorTest {

    private static final Instant NOW = Instant.parse("2026-05-28T00:00:00Z");
    private static final UUID BOOKING_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID HOST_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PAYMENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID IDEMPOTENCY_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private BookingRepository bookingRepository;
    private BookingPaymentRepository bookingPaymentRepository;
    private PaymentTransactionRepository paymentTransactionRepository;
    private AvailabilityReserver availabilityReserver;
    private IdempotencyService idempotencyService;
    private IdempotencyFailureMarker idempotencyFailureMarker;
    private PaymentProviderRouter paymentProviderRouter;
    private com.rentflow.common.exception.CorrelationIdHelper correlationIdHelper;
    private PaymentProvider paymentProvider;
    private ExpireHostApprovalsProcessor processor;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        bookingPaymentRepository = mock(BookingPaymentRepository.class);
        paymentTransactionRepository = mock(PaymentTransactionRepository.class);
        availabilityReserver = mock(AvailabilityReserver.class);
        idempotencyService = mock(IdempotencyService.class);
        idempotencyFailureMarker = mock(IdempotencyFailureMarker.class);
        paymentProviderRouter = mock(PaymentProviderRouter.class);
        correlationIdHelper = mock(com.rentflow.common.exception.CorrelationIdHelper.class);
        paymentProvider = mock(PaymentProvider.class);

        when(correlationIdHelper.getOrGenerate()).thenReturn("corr-1");
        when(paymentProviderRouter.route(PaymentProviderType.COREBANK)).thenReturn(paymentProvider);
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        processor = new ExpireHostApprovalsProcessor(
                bookingRepository,
                bookingPaymentRepository,
                paymentTransactionRepository,
                availabilityReserver,
                idempotencyService,
                idempotencyFailureMarker,
                paymentProviderRouter,
                correlationIdHelper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                300);
    }

    @Test
    void expiresPendingHostApprovalWhenVoidSucceeds() {
        Booking booking = pendingBooking();
        BookingPayment payment = authorizedPayment();
        List<AvailabilityCalendar> rows = heldRows();

        when(bookingRepository.findExpiredHostApprovalCandidatesForUpdate(NOW, 100)).thenReturn(List.of(booking));
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(eq(HOST_ID), eq(IdempotencyScope.HOST_EXPIRE_BOOKING_APPROVAL), any(), eq("hash")))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(availabilityReserver.lockForBooking(booking)).thenReturn(rows);
        when(paymentProvider.voidAuthorization(any())).thenReturn(new VoidResult("VOIDED", "{\"status\":\"VOIDED\"}"));

        int processed = processor.processBatch(100);

        assertThat(processed).isEqualTo(1);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VOIDED);
        assertThat(rows).allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.FREE));
        verify(idempotencyService).complete(IDEMPOTENCY_ID, 200, "{\"status\":\"EXPIRED\"}");
        verify(idempotencyFailureMarker, never()).markFailed(any());
    }

    @Test
    void voidFailureKeepsBookingPendingAndMarksRetryMetadata() {
        Booking booking = pendingBooking();
        BookingPayment payment = authorizedPayment();
        List<AvailabilityCalendar> rows = heldRows();

        when(bookingRepository.findExpiredHostApprovalCandidatesForUpdate(NOW, 100)).thenReturn(List.of(booking));
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(eq(HOST_ID), eq(IdempotencyScope.HOST_EXPIRE_BOOKING_APPROVAL), any(), eq("hash")))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(availabilityReserver.lockForBooking(booking)).thenReturn(rows);
        when(paymentProvider.voidAuthorization(any())).thenThrow(new PaymentProviderUnavailableException("provider down"));

        int processed = processor.processBatch(100);

        assertThat(processed).isZero();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_HOST_APPROVAL);
        assertThat(payment.isVoidRetryRequired()).isTrue();
        assertThat(payment.getVoidRetryCount()).isEqualTo(1);
        assertThat(payment.getVoidRetryLastError()).contains("provider down");
        verify(idempotencyFailureMarker).markFailed(IDEMPOTENCY_ID);
    }

    @Test
    void replayResolutionSkipsCandidate() {
        Booking booking = pendingBooking();
        when(bookingRepository.findExpiredHostApprovalCandidatesForUpdate(NOW, 100)).thenReturn(List.of(booking));
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(eq(HOST_ID), eq(IdempotencyScope.HOST_EXPIRE_BOOKING_APPROVAL), any(), eq("hash")))
                .thenReturn(IdempotencyResolution.replay(200, "{\"status\":\"EXPIRED\"}"));

        int processed = processor.processBatch(100);

        assertThat(processed).isZero();
        verify(bookingPaymentRepository, never()).findByBookingIdForUpdate(any());
    }

    private Booking pendingBooking() {
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setHostId(HOST_ID);
        booking.setListingId(UUID.fromString("55555555-5555-4555-8555-555555555555"));
        booking.setStatus(BookingStatus.PENDING_HOST_APPROVAL);
        booking.setPickupDate(LocalDate.of(2026, 6, 1));
        booking.setReturnDate(LocalDate.of(2026, 6, 3));
        booking.setHostApprovalExpiresAt(NOW.minusSeconds(60));
        booking.setHoldToken(UUID.randomUUID());
        return booking;
    }

    private BookingPayment authorizedPayment() {
        BookingPayment payment = new BookingPayment();
        payment.setId(PAYMENT_ID);
        payment.setBookingId(BOOKING_ID);
        payment.setProvider(PaymentProviderType.COREBANK);
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthorizedAmount(new BigDecimal("1500000.00"));
        payment.setCapturedAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setCurrency("VND");
        payment.setProviderHoldId("hold-1");
        payment.setVoidRetryCount(0);
        return payment;
    }

    private List<AvailabilityCalendar> heldRows() {
        UUID listingId = UUID.fromString("55555555-5555-4555-8555-555555555555");
        AvailabilityCalendar first = new AvailabilityCalendar(listingId, LocalDate.of(2026, 6, 1));
        first.setStatus(AvailabilityStatus.HOLD);
        first.setBookingId(BOOKING_ID);
        AvailabilityCalendar second = new AvailabilityCalendar(listingId, LocalDate.of(2026, 6, 2));
        second.setStatus(AvailabilityStatus.HOLD);
        second.setBookingId(BOOKING_ID);
        return List.of(first, second);
    }
}
