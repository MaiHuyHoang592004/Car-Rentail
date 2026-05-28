package com.rentflow.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rentflow.auth.entity.Role;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.IdempotencyKeyConflictException;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.exception.EmailNotVerifiedException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.EmailVerificationPolicy;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.payment.dto.AuthorizePaymentRequest;
import com.rentflow.payment.dto.AuthorizePaymentResponse;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentBank;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.provider.AuthorizeCommand;
import com.rentflow.payment.provider.AuthorizeResult;
import com.rentflow.payment.provider.PaymentProvider;
import com.rentflow.payment.provider.PaymentProviderRouter;
import com.rentflow.payment.provider.TransferInstruction;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankTransferAuthorizeServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-28T00:00:00Z");
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOOKING_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BANK_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID IDEMPOTENCY_KEY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String IDEMPOTENCY_KEY = "8b71f8d2-9e1d-4f7a-bbe6-334c3816df91";
    private static final String REQUEST_HASH = "authorize-hash";

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingPaymentRepository bookingPaymentRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private IdempotencyService idempotencyService;
    @Mock private IdempotencyFailureMarker idempotencyFailureMarker;
    @Mock private SecurityContext securityContext;
    @Mock private EmailVerificationPolicy emailVerificationPolicy;
    @Mock private PaymentProviderRouter paymentProviderRouter;
    @Mock private PaymentProvider paymentProvider;

    private BankTransferAuthorizeService authorizeService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        authorizeService = new BankTransferAuthorizeService(
                bookingRepository,
                bookingPaymentRepository,
                paymentTransactionRepository,
                idempotencyService,
                idempotencyFailureMarker,
                paymentProviderRouter,
                securityContext,
                emailVerificationPolicy,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new PaymentBookingSnapshotParser(objectMapper),
                new AuthorizePaymentResponseFactory(),
                false);
    }

    @Test
    void authorizeForBankTransferCreatesPendingTransferPaymentAndTransaction() {
        AuthorizePaymentRequest request = new AuthorizePaymentRequest(BANK_ID, PaymentMethod.BANK_TRANSFER_QR);
        Booking booking = heldBooking();
        PaymentBank bank = bank(PaymentMethod.BANK_TRANSFER_QR, PaymentProviderType.VIETQR_MANUAL);
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn(REQUEST_HASH);
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.AUTHORIZE_PAYMENT, IDEMPOTENCY_KEY, REQUEST_HASH))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_KEY_ID));
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.empty());
        when(paymentProviderRouter.route(PaymentProviderType.VIETQR_MANUAL)).thenReturn(paymentProvider);
        when(paymentProvider.authorize(any())).thenReturn(new AuthorizeResult(
                PaymentProviderType.VIETQR_MANUAL,
                PaymentStatus.PENDING_TRANSFER,
                BigDecimal.ZERO,
                new TransferInstruction(
                        "VCB",
                        "970436",
                        "1234567890",
                        "RENTFLOW ESCROW",
                        new BigDecimal("1400000.00"),
                        "RENTFLOW " + BOOKING_ID,
                        "manual-vietqr:test"),
                "TRANSFER_INSTRUCTION_GENERATED",
                null,
                null,
                null));
        when(bookingPaymentRepository.save(any(BookingPayment.class))).thenAnswer(invocation -> {
            BookingPayment payment = invocation.getArgument(0);
            payment.setId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
            return payment;
        });

        AuthorizePaymentResponse response = authorizeService.authorizeBookingPayment(BOOKING_ID, IDEMPOTENCY_KEY, request, bank);

        assertThat(response.booking().status()).isEqualTo(BookingStatus.HELD);
        assertThat(response.booking().pickupDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(response.payment().status()).isEqualTo(PaymentStatus.PENDING_TRANSFER);
        assertThat(response.payment().authorizedAmount()).isEqualByComparingTo("0");
        assertThat(response.payment().externalOrderRef()).isEqualTo("rentflow:booking:" + BOOKING_ID);
        assertThat(response.payment().transferInstruction()).isNotNull();

        ArgumentCaptor<AuthorizeCommand> commandCaptor = ArgumentCaptor.forClass(AuthorizeCommand.class);
        verify(paymentProvider).authorize(commandCaptor.capture());
        assertThat(commandCaptor.getValue().externalOrderRef()).isEqualTo("rentflow:booking:" + BOOKING_ID);
        assertThat(commandCaptor.getValue().totalAmount()).isEqualByComparingTo("1400000.00");

        ArgumentCaptor<BookingPayment> paymentCaptor = ArgumentCaptor.forClass(BookingPayment.class);
        verify(bookingPaymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING_TRANSFER);
        assertThat(paymentCaptor.getValue().getSelectedBankId()).isEqualTo(BANK_ID);
        assertThat(paymentCaptor.getValue().getCurrency()).isEqualTo("VND");

        ArgumentCaptor<PaymentTransaction> transactionCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getAmount()).isEqualByComparingTo("0");
        assertThat(transactionCaptor.getValue().getProvider()).isEqualTo(PaymentProviderType.VIETQR_MANUAL);
        assertThat(transactionCaptor.getValue().getIdempotencyKeyId()).isEqualTo(IDEMPOTENCY_KEY_ID);

        verify(idempotencyService).complete(eq(IDEMPOTENCY_KEY_ID), eq(200), any());
        verify(idempotencyFailureMarker, never()).markFailed(any());
    }

    @Test
    void authorizeRejectsBankPaymentMethodMismatch() {
        AuthorizePaymentRequest request = new AuthorizePaymentRequest(BANK_ID, PaymentMethod.COREBANK_TRANSFER);
        Booking booking = heldBooking();
        PaymentBank bank = bank(PaymentMethod.BANK_TRANSFER_QR, PaymentProviderType.VIETQR_MANUAL);
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn(REQUEST_HASH);
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.AUTHORIZE_PAYMENT, IDEMPOTENCY_KEY, REQUEST_HASH))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_KEY_ID));
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> authorizeService.authorizeBookingPayment(BOOKING_ID, IDEMPOTENCY_KEY, request, bank))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("requested payment method");

        verify(idempotencyFailureMarker).markFailed(IDEMPOTENCY_KEY_ID);
        verifyNoInteractions(paymentProviderRouter, paymentTransactionRepository);
    }

    @Test
    void authorizeReplayReturnsStoredResponseWithoutBusinessLogic() throws Exception {
        AuthorizePaymentRequest request = new AuthorizePaymentRequest(BANK_ID, PaymentMethod.BANK_TRANSFER_QR);
        AuthorizePaymentResponse replayed = new AuthorizePaymentResponse(
                new AuthorizePaymentResponse.BookingSummary(
                        BOOKING_ID,
                        BookingStatus.HELD,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 3),
                        new BigDecimal("1400000.00"),
                        "VND"),
                new AuthorizePaymentResponse.PaymentSummary(
                        UUID.fromString("55555555-5555-5555-5555-555555555555"),
                        PaymentStatus.PENDING_TRANSFER,
                        PaymentMethod.BANK_TRANSFER_QR,
                        PaymentProviderType.VIETQR_MANUAL,
                        "rentflow:booking:" + BOOKING_ID,
                        null,
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "VND",
                        null));
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn(REQUEST_HASH);
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.AUTHORIZE_PAYMENT, IDEMPOTENCY_KEY, REQUEST_HASH))
                .thenReturn(IdempotencyResolution.replay(200, objectMapper.writeValueAsString(replayed)));

        AuthorizePaymentResponse result = authorizeService.authorizeBookingPayment(
                BOOKING_ID,
                IDEMPOTENCY_KEY,
                request,
                bank(PaymentMethod.BANK_TRANSFER_QR, PaymentProviderType.VIETQR_MANUAL));

        assertThat(result).isEqualTo(replayed);
        verifyNoInteractions(bookingRepository, bookingPaymentRepository, paymentTransactionRepository, paymentProviderRouter);
    }

    @Test
    void authorizeConflictOnSameKeyDifferentBodyBubblesUp() {
        AuthorizePaymentRequest request = new AuthorizePaymentRequest(BANK_ID, PaymentMethod.BANK_TRANSFER_QR);
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn(REQUEST_HASH);
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.AUTHORIZE_PAYMENT, IDEMPOTENCY_KEY, REQUEST_HASH))
                .thenThrow(new IdempotencyKeyConflictException());

        assertThatThrownBy(() -> authorizeService.authorizeBookingPayment(
                BOOKING_ID,
                IDEMPOTENCY_KEY,
                request,
                bank(PaymentMethod.BANK_TRANSFER_QR, PaymentProviderType.VIETQR_MANUAL)))
                .isInstanceOf(IdempotencyKeyConflictException.class)
                .hasFieldOrPropertyWithValue("code", "IDEMPOTENCY_KEY_CONFLICT");

        verifyNoInteractions(bookingRepository, bookingPaymentRepository, paymentTransactionRepository, paymentProviderRouter);
    }

    @Test
    void authorizeFailsWhenEmailNotVerifiedAndGateEnabled() {
        authorizeService = new BankTransferAuthorizeService(
                bookingRepository,
                bookingPaymentRepository,
                paymentTransactionRepository,
                idempotencyService,
                idempotencyFailureMarker,
                paymentProviderRouter,
                securityContext,
                emailVerificationPolicy,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new PaymentBookingSnapshotParser(objectMapper),
                new AuthorizePaymentResponseFactory(),
                true);
        AuthorizePaymentRequest request = new AuthorizePaymentRequest(BANK_ID, PaymentMethod.BANK_TRANSFER_QR);
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn(REQUEST_HASH);
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.AUTHORIZE_PAYMENT, IDEMPOTENCY_KEY, REQUEST_HASH))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_KEY_ID));
        org.mockito.Mockito.doThrow(new EmailNotVerifiedException())
                .when(emailVerificationPolicy).requireVerifiedEmail(CUSTOMER_ID);

        assertThatThrownBy(() -> authorizeService.authorizeBookingPayment(
                BOOKING_ID,
                IDEMPOTENCY_KEY,
                request,
                bank(PaymentMethod.BANK_TRANSFER_QR, PaymentProviderType.VIETQR_MANUAL)))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_NOT_VERIFIED");

        verify(idempotencyFailureMarker).markFailed(IDEMPOTENCY_KEY_ID);
        verifyNoInteractions(bookingRepository, bookingPaymentRepository, paymentTransactionRepository, paymentProviderRouter);
    }

    private Booking heldBooking() {
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setCustomerId(CUSTOMER_ID);
        booking.setListingId(UUID.fromString("66666666-6666-6666-6666-666666666666"));
        booking.setPickupDate(LocalDate.of(2026, 6, 1));
        booking.setReturnDate(LocalDate.of(2026, 6, 3));
        booking.setStatus(BookingStatus.HELD);
        booking.setHoldExpiresAt(NOW.plusSeconds(900));
        booking.setPriceSnapshot("""
                {"totalAmount":1400000.00,"currency":"VND","extras":[]}
                """);
        return booking;
    }

    private PaymentBank bank(PaymentMethod paymentMethod, PaymentProviderType providerType) {
        PaymentBank bank = new PaymentBank();
        bank.setId(BANK_ID);
        bank.setCode("VCB");
        bank.setBin("970436");
        bank.setPaymentMethod(paymentMethod);
        bank.setProvider(providerType);
        bank.setActive(true);
        return bank;
    }
}
