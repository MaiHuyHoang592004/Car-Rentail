package com.rentflow.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rentflow.auth.entity.Role;
import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.mapper.BookingMapper;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.PaymentProviderUnavailableException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.common.web.PageResponse;
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
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HostBookingApprovalServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-28T00:00:00Z");
    private static final UUID BOOKING_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID LISTING_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID HOST_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID CUSTOMER_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID PAYMENT_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID IDEMPOTENCY_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final String IDEMPOTENCY_KEY = "8b71f8d2-9e1d-4f7a-bbe6-334c3816df91";

    private BookingRepository bookingRepository;
    private BookingPaymentRepository bookingPaymentRepository;
    private PaymentTransactionRepository paymentTransactionRepository;
    private AvailabilityReserver availabilityReserver;
    private BookingMapper bookingMapper;
    private SecurityContext securityContext;
    private IdempotencyService idempotencyService;
    private IdempotencyFailureMarker idempotencyFailureMarker;
    private PaymentProviderRouter paymentProviderRouter;
    private com.rentflow.common.exception.CorrelationIdHelper correlationIdHelper;
    private PaymentProvider paymentProvider;
    private HostBookingApprovalService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        bookingPaymentRepository = mock(BookingPaymentRepository.class);
        paymentTransactionRepository = mock(PaymentTransactionRepository.class);
        availabilityReserver = mock(AvailabilityReserver.class);
        bookingMapper = mock(BookingMapper.class);
        securityContext = mock(SecurityContext.class);
        idempotencyService = mock(IdempotencyService.class);
        idempotencyFailureMarker = mock(IdempotencyFailureMarker.class);
        paymentProviderRouter = mock(PaymentProviderRouter.class);
        correlationIdHelper = mock(com.rentflow.common.exception.CorrelationIdHelper.class);
        paymentProvider = mock(PaymentProvider.class);
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        when(correlationIdHelper.getOrGenerate()).thenReturn("corr-1");
        when(paymentProviderRouter.route(PaymentProviderType.COREBANK)).thenReturn(paymentProvider);
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service = new HostBookingApprovalService(
                bookingRepository,
                bookingPaymentRepository,
                paymentTransactionRepository,
                availabilityReserver,
                bookingMapper,
                securityContext,
                idempotencyService,
                idempotencyFailureMarker,
                paymentProviderRouter,
                correlationIdHelper,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listHostBookingsDelegatesByStatus() {
        when(securityContext.currentUserId()).thenReturn(HOST_ID);
        Booking booking = pendingBooking();
        when(bookingRepository.findByHostIdAndStatusOrderByCreatedAtDesc(HOST_ID, BookingStatus.PENDING_HOST_APPROVAL, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(booking)));
        PageResponse<BookingSummaryResponse> expected = new PageResponse<>(List.of(), 0, 20, 0, 0);
        when(bookingMapper.toSummaryPage(any())).thenReturn(expected);

        PageResponse<BookingSummaryResponse> response = service.listHostBookings(
                BookingStatus.PENDING_HOST_APPROVAL,
                PageRequest.of(0, 20));

        assertThat(response).isEqualTo(expected);
        verify(securityContext).requireRole(Role.HOST);
    }

    @Test
    void approveReplayReturnsStoredResponse() throws Exception {
        when(securityContext.currentUserId()).thenReturn(HOST_ID);
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        BookingResponse replayed = bookingResponse(BookingStatus.CONFIRMED);
        when(idempotencyService.resolve(HOST_ID, IdempotencyScope.HOST_APPROVE_BOOKING, IDEMPOTENCY_KEY, "hash"))
                .thenReturn(IdempotencyResolution.replay(200, objectMapper.writeValueAsString(replayed)));

        BookingResponse response = service.approveBooking(BOOKING_ID, IDEMPOTENCY_KEY);

        assertThat(response).isEqualTo(replayed);
        verifyNoInteractions(bookingRepository, bookingPaymentRepository, availabilityReserver);
    }

    @Test
    void approveTransitionsBookingAndAvailability() {
        when(securityContext.currentUserId()).thenReturn(HOST_ID);
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(HOST_ID, IdempotencyScope.HOST_APPROVE_BOOKING, IDEMPOTENCY_KEY, "hash"))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
        Booking booking = pendingBooking();
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        BookingPayment payment = authorizedPayment();
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(payment));
        List<AvailabilityCalendar> rows = heldRows();
        when(availabilityReserver.lockForBooking(booking)).thenReturn(rows);
        BookingResponse mapped = bookingResponse(BookingStatus.CONFIRMED);
        when(bookingMapper.toResponse(booking)).thenReturn(mapped);

        BookingResponse response = service.approveBooking(BOOKING_ID, IDEMPOTENCY_KEY);

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getHostApprovalExpiresAt()).isNull();
        assertThat(rows).allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.BOOKED));
        verify(idempotencyService).complete(eq(IDEMPOTENCY_ID), eq(200), any());
    }

    @Test
    void rejectVoidsThenTransitionsBookingAndAvailability() {
        when(securityContext.currentUserId()).thenReturn(HOST_ID);
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(HOST_ID, IdempotencyScope.HOST_REJECT_BOOKING, IDEMPOTENCY_KEY, "hash"))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
        Booking booking = pendingBooking();
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        BookingPayment payment = authorizedPayment();
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(payment));
        List<AvailabilityCalendar> rows = heldRows();
        when(availabilityReserver.lockForBooking(booking)).thenReturn(rows);
        when(paymentProvider.voidAuthorization(any())).thenReturn(new VoidResult("VOIDED", "{\"status\":\"VOIDED\"}"));
        BookingResponse mapped = bookingResponse(BookingStatus.REJECTED);
        when(bookingMapper.toResponse(booking)).thenReturn(mapped);

        BookingResponse response = service.rejectBooking(BOOKING_ID, IDEMPOTENCY_KEY);

        assertThat(response.status()).isEqualTo(BookingStatus.REJECTED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REJECTED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VOIDED);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.FREE);
            assertThat(row.getBookingId()).isNull();
        });
        verify(paymentTransactionRepository, org.mockito.Mockito.times(2)).save(any(PaymentTransaction.class));
    }

    @Test
    void rejectVoidFailureDoesNotFinalizeBooking() {
        when(securityContext.currentUserId()).thenReturn(HOST_ID);
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(HOST_ID, IdempotencyScope.HOST_REJECT_BOOKING, IDEMPOTENCY_KEY, "hash"))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
        Booking booking = pendingBooking();
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        BookingPayment payment = authorizedPayment();
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(availabilityReserver.lockForBooking(booking)).thenReturn(heldRows());
        when(paymentProvider.voidAuthorization(any())).thenThrow(new PaymentProviderUnavailableException("provider down"));

        assertThatThrownBy(() -> service.rejectBooking(BOOKING_ID, IDEMPOTENCY_KEY))
                .isInstanceOf(PaymentProviderUnavailableException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_HOST_APPROVAL);
        verify(idempotencyFailureMarker).markFailed(IDEMPOTENCY_ID);
    }

    @Test
    void approveRejectsDifferentHostAsNotFound() {
        UUID otherHost = UUID.fromString("77777777-7777-4777-8777-777777777777");
        when(securityContext.currentUserId()).thenReturn(otherHost);
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(otherHost, IdempotencyScope.HOST_APPROVE_BOOKING, IDEMPOTENCY_KEY, "hash"))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(pendingBooking()));

        assertThatThrownBy(() -> service.approveBooking(BOOKING_ID, IDEMPOTENCY_KEY))
                .isInstanceOf(BookingNotFoundException.class);

        verify(idempotencyFailureMarker).markFailed(IDEMPOTENCY_ID);
    }

    @Test
    void rejectRequiresCoreBankAuthorizedPayment() {
        when(securityContext.currentUserId()).thenReturn(HOST_ID);
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(HOST_ID, IdempotencyScope.HOST_REJECT_BOOKING, IDEMPOTENCY_KEY, "hash"))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(pendingBooking()));
        BookingPayment payment = authorizedPayment();
        payment.setProvider(PaymentProviderType.VIETQR_MANUAL);
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.rejectBooking(BOOKING_ID, IDEMPOTENCY_KEY))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_PROVIDER_UNSUPPORTED");

        verify(paymentProvider, never()).voidAuthorization(any());
    }

    private Booking pendingBooking() {
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setListingId(LISTING_ID);
        booking.setCustomerId(CUSTOMER_ID);
        booking.setHostId(HOST_ID);
        booking.setStatus(BookingStatus.PENDING_HOST_APPROVAL);
        booking.setPickupDate(LocalDate.of(2026, 6, 1));
        booking.setReturnDate(LocalDate.of(2026, 6, 3));
        booking.setHostApprovalExpiresAt(NOW.plusSeconds(3600));
        booking.setHoldToken(UUID.randomUUID());
        booking.setCreatedAt(NOW);
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
        payment.setProviderPaymentOrderId("po-1");
        payment.setProviderHoldId("hold-1");
        return payment;
    }

    private List<AvailabilityCalendar> heldRows() {
        AvailabilityCalendar first = new AvailabilityCalendar(LISTING_ID, LocalDate.of(2026, 6, 1));
        first.setStatus(AvailabilityStatus.HOLD);
        first.setBookingId(BOOKING_ID);
        AvailabilityCalendar second = new AvailabilityCalendar(LISTING_ID, LocalDate.of(2026, 6, 2));
        second.setStatus(AvailabilityStatus.HOLD);
        second.setBookingId(BOOKING_ID);
        return List.of(first, second);
    }

    private BookingResponse bookingResponse(BookingStatus status) {
        return new BookingResponse(
                BOOKING_ID,
                status,
                LISTING_ID,
                "Toyota Vios 2022",
                CUSTOMER_ID,
                HOST_ID,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                "Hanoi",
                "Hanoi",
                null,
                new BigDecimal("1500000.00"),
                "VND",
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode(),
                NOW);
    }
}
