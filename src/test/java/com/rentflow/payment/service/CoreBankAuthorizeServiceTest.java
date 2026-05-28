package com.rentflow.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rentflow.auth.entity.Role;
import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.EmailNotVerifiedException;
import com.rentflow.common.exception.PaymentProviderUnavailableException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.EmailVerificationPolicy;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.payment.dto.AuthorizePaymentRequest;
import com.rentflow.payment.dto.AuthorizePaymentResponse;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.CustomerPaymentAccount;
import com.rentflow.payment.entity.PaymentBank;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.entity.PaymentTransactionStatus;
import com.rentflow.payment.provider.PaymentProvider;
import com.rentflow.payment.provider.PaymentProviderRouter;
import com.rentflow.payment.provider.AuthorizeResult;
import com.rentflow.payment.provider.corebank.CoreBankAuthorizationFailedException;
import com.rentflow.payment.provider.VoidResult;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.CustomerPaymentAccountRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoreBankAuthorizeServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-28T00:00:00Z");
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOOKING_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BANK_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID IDEMPOTENCY_KEY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingPaymentRepository bookingPaymentRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private CustomerPaymentAccountRepository customerPaymentAccountRepository;
    @Mock private AvailabilityCalendarRepository availabilityCalendarRepository;
    @Mock private IdempotencyService idempotencyService;
    @Mock private IdempotencyFailureMarker idempotencyFailureMarker;
    @Mock private SecurityContext securityContext;
    @Mock private EmailVerificationPolicy emailVerificationPolicy;
    @Mock private PaymentProviderRouter paymentProviderRouter;
    @Mock private PaymentProvider coreBankProvider;
    @Mock private CorrelationIdHelper correlationIdHelper;

    private CoreBankAuthorizeService authorizeService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        PlatformTransactionManager transactionManager = new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
        authorizeService = new CoreBankAuthorizeService(
                bookingRepository,
                bookingPaymentRepository,
                paymentTransactionRepository,
                customerPaymentAccountRepository,
                availabilityCalendarRepository,
                idempotencyService,
                idempotencyFailureMarker,
                securityContext,
                emailVerificationPolicy,
                new PaymentBookingSnapshotParser(objectMapper),
                new AuthorizePaymentResponseFactory(),
                paymentProviderRouter,
                correlationIdHelper,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager,
                false);
        lenient().when(paymentProviderRouter.route(PaymentProviderType.COREBANK)).thenReturn(coreBankProvider);
    }

    @Test
    void missingCustomerPaymentAccountReturnsConflict() {
        AuthorizePaymentRequest request = new AuthorizePaymentRequest(BANK_ID, PaymentMethod.COREBANK_TRANSFER);
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.AUTHORIZE_PAYMENT, "key", "hash"))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_KEY_ID));
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking(true)));
        when(customerPaymentAccountRepository.findByUserIdAndProviderAndActiveTrue(CUSTOMER_ID, PaymentProviderType.COREBANK))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorizeService.authorizeBookingPayment(BOOKING_ID, "key", request, bank()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_ACCOUNT_NOT_LINKED");

        verify(idempotencyFailureMarker).markFailed(IDEMPOTENCY_KEY_ID);
    }

    @Test
    void authorizeFailsWhenEmailNotVerifiedAndGateEnabled() {
        PlatformTransactionManager transactionManager = new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
        authorizeService = new CoreBankAuthorizeService(
                bookingRepository,
                bookingPaymentRepository,
                paymentTransactionRepository,
                customerPaymentAccountRepository,
                availabilityCalendarRepository,
                idempotencyService,
                idempotencyFailureMarker,
                securityContext,
                emailVerificationPolicy,
                new PaymentBookingSnapshotParser(objectMapper),
                new AuthorizePaymentResponseFactory(),
                paymentProviderRouter,
                correlationIdHelper,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager,
                true);
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.AUTHORIZE_PAYMENT, "key", "hash"))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_KEY_ID));
        org.mockito.Mockito.doThrow(new EmailNotVerifiedException())
                .when(emailVerificationPolicy).requireVerifiedEmail(CUSTOMER_ID);

        assertThatThrownBy(() -> authorizeService.authorizeBookingPayment(
                BOOKING_ID,
                "key",
                new AuthorizePaymentRequest(BANK_ID, PaymentMethod.COREBANK_TRANSFER),
                bank()))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_NOT_VERIFIED");

        verify(idempotencyFailureMarker).markFailed(IDEMPOTENCY_KEY_ID);
    }

    @Test
    void coreBankSuccessWithInstantBookConfirmsBookingAndBooksAvailability() {
        Booking booking = booking(true);
        BookingPayment payment = new BookingPayment();
        payment.setId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(UUID.fromString("66666666-6666-6666-6666-666666666666"));
        List<AvailabilityCalendar> rows = heldAvailabilityRows(booking);

        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.AUTHORIZE_PAYMENT, "key", "hash"))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_KEY_ID));
        when(correlationIdHelper.getOrGenerate()).thenReturn("correlation-1");
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(customerPaymentAccountRepository.findByUserIdAndProviderAndActiveTrue(CUSTOMER_ID, PaymentProviderType.COREBANK))
                .thenReturn(Optional.of(customerPaymentAccount()));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID))
                .thenReturn(Optional.empty(), Optional.of(payment));
        when(bookingPaymentRepository.save(any(BookingPayment.class))).thenAnswer(invocation -> {
            BookingPayment saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(payment.getId());
            }
            return saved;
        });
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(transaction.getId());
            }
            return saved;
        });
        when(paymentTransactionRepository.findByIdForUpdate(transaction.getId())).thenReturn(Optional.of(transaction));
        when(availabilityCalendarRepository.findForBookingRangeForUpdate(booking.getListingId(), booking.getPickupDate(), booking.getReturnDate()))
                .thenReturn(rows);
        when(coreBankProvider.authorize(any())).thenReturn(new AuthorizeResult(
                PaymentProviderType.COREBANK,
                PaymentStatus.AUTHORIZED,
                new BigDecimal("1400000.00"),
                null,
                "AUTHORIZED",
                "payment-order-1",
                "hold-1",
                "{\"paymentOrderId\":\"payment-order-1\",\"holdId\":\"hold-1\",\"status\":\"AUTHORIZED\"}"));

        AuthorizePaymentResponse response = authorizeService.authorizeBookingPayment(
                BOOKING_ID,
                "key",
                new AuthorizePaymentRequest(BANK_ID, PaymentMethod.COREBANK_TRANSFER),
                bank());

        assertThat(response.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.payment().status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(response.payment().providerPaymentOrderId()).isEqualTo("payment-order-1");
        assertThat(response.payment().providerHoldId()).isEqualTo("hold-1");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getHoldToken()).isNull();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.BOOKED);
            assertThat(row.getHoldToken()).isNull();
        });
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.getAuthorizedAmount()).isEqualByComparingTo("1400000.00");
        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.SUCCEEDED);
        verify(idempotencyService).complete(eq(IDEMPOTENCY_KEY_ID), eq(200), any());
    }

    @Test
    void coreBankSuccessWithManualApprovalMovesBookingToPendingHostApproval() {
        Booking booking = booking(false);
        BookingPayment payment = new BookingPayment();
        payment.setId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(UUID.fromString("66666666-6666-6666-6666-666666666666"));
        List<AvailabilityCalendar> rows = heldAvailabilityRows(booking);

        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.AUTHORIZE_PAYMENT, "key", "hash"))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_KEY_ID));
        when(correlationIdHelper.getOrGenerate()).thenReturn("correlation-1");
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(customerPaymentAccountRepository.findByUserIdAndProviderAndActiveTrue(CUSTOMER_ID, PaymentProviderType.COREBANK))
                .thenReturn(Optional.of(customerPaymentAccount()));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID))
                .thenReturn(Optional.empty(), Optional.of(payment));
        when(bookingPaymentRepository.save(any(BookingPayment.class))).thenAnswer(invocation -> {
            BookingPayment saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(payment.getId());
            }
            return saved;
        });
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(transaction.getId());
            }
            return saved;
        });
        when(paymentTransactionRepository.findByIdForUpdate(transaction.getId())).thenReturn(Optional.of(transaction));
        when(availabilityCalendarRepository.findForBookingRangeForUpdate(booking.getListingId(), booking.getPickupDate(), booking.getReturnDate()))
                .thenReturn(rows);
        when(coreBankProvider.authorize(any())).thenReturn(new AuthorizeResult(
                PaymentProviderType.COREBANK,
                PaymentStatus.AUTHORIZED,
                new BigDecimal("1400000.00"),
                null,
                "AUTHORIZED",
                "payment-order-1",
                "hold-1",
                "{\"paymentOrderId\":\"payment-order-1\",\"holdId\":\"hold-1\",\"status\":\"AUTHORIZED\"}"));

        AuthorizePaymentResponse response = authorizeService.authorizeBookingPayment(
                BOOKING_ID,
                "key",
                new AuthorizePaymentRequest(BANK_ID, PaymentMethod.COREBANK_TRANSFER),
                bank());

        assertThat(response.booking().status()).isEqualTo(BookingStatus.PENDING_HOST_APPROVAL);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_HOST_APPROVAL);
        assertThat(booking.getHostApprovalExpiresAt()).isNotNull();
        assertThat(rows).allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.HOLD));
    }

    @Test
    void coreBankDeclineMarksPaymentAndTransactionFailed() {
        Booking booking = booking(true);
        BookingPayment payment = new BookingPayment();
        payment.setId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(UUID.fromString("66666666-6666-6666-6666-666666666666"));

        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.AUTHORIZE_PAYMENT, "key", "hash"))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_KEY_ID));
        when(correlationIdHelper.getOrGenerate()).thenReturn("correlation-1");
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(customerPaymentAccountRepository.findByUserIdAndProviderAndActiveTrue(CUSTOMER_ID, PaymentProviderType.COREBANK))
                .thenReturn(Optional.of(customerPaymentAccount()));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID))
                .thenReturn(Optional.empty(), Optional.of(payment));
        when(bookingPaymentRepository.save(any(BookingPayment.class))).thenAnswer(invocation -> {
            BookingPayment saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(payment.getId());
            }
            return saved;
        });
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(transaction.getId());
            }
            return saved;
        });
        when(paymentTransactionRepository.findByIdForUpdate(transaction.getId())).thenReturn(Optional.of(transaction));
        when(coreBankProvider.authorize(any())).thenThrow(new CoreBankAuthorizationFailedException(
                "Insufficient funds",
                "DECLINED",
                "INSUFFICIENT_FUNDS",
                "Insufficient funds",
                "{\"code\":\"INSUFFICIENT_FUNDS\"}"));

        assertThatThrownBy(() -> authorizeService.authorizeBookingPayment(
                BOOKING_ID,
                "key",
                new AuthorizePaymentRequest(BANK_ID, PaymentMethod.COREBANK_TRANSFER),
                bank()))
                .isInstanceOf(CoreBankAuthorizationFailedException.class)
                .hasMessageContaining("Insufficient funds");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
        verify(idempotencyFailureMarker).markFailed(IDEMPOTENCY_KEY_ID);
    }

    @Test
    void tx2FailureTriggersCompensationAndLeavesPaymentFailed() {
        Booking booking = booking(true);
        BookingPayment payment = new BookingPayment();
        payment.setId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(UUID.fromString("66666666-6666-6666-6666-666666666666"));
        List<AvailabilityCalendar> rows = heldAvailabilityRows(booking);
        rows.get(0).setStatus(AvailabilityStatus.BLOCKED);

        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.AUTHORIZE_PAYMENT, "key", "hash"))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_KEY_ID));
        when(correlationIdHelper.getOrGenerate()).thenReturn("correlation-1");
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(customerPaymentAccountRepository.findByUserIdAndProviderAndActiveTrue(CUSTOMER_ID, PaymentProviderType.COREBANK))
                .thenReturn(Optional.of(customerPaymentAccount()));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID))
                .thenReturn(Optional.empty(), Optional.of(payment), Optional.of(payment), Optional.of(payment));
        when(bookingPaymentRepository.save(any(BookingPayment.class))).thenAnswer(invocation -> {
            BookingPayment saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(payment.getId());
            }
            return saved;
        });
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(transaction.getId());
            }
            return saved;
        });
        when(paymentTransactionRepository.findByIdForUpdate(transaction.getId()))
                .thenReturn(Optional.of(transaction), Optional.of(transaction));
        when(availabilityCalendarRepository.findForBookingRangeForUpdate(booking.getListingId(), booking.getPickupDate(), booking.getReturnDate()))
                .thenReturn(rows);
        when(coreBankProvider.authorize(any())).thenReturn(new AuthorizeResult(
                PaymentProviderType.COREBANK,
                PaymentStatus.AUTHORIZED,
                new BigDecimal("1400000.00"),
                null,
                "AUTHORIZED",
                "payment-order-1",
                "hold-1",
                "{\"paymentOrderId\":\"payment-order-1\",\"holdId\":\"hold-1\",\"status\":\"AUTHORIZED\"}"));
        when(coreBankProvider.voidAuthorization(any()))
                .thenReturn(new VoidResult("VOIDED", "{\"holdId\":\"hold-1\",\"status\":\"VOIDED\"}"));

        assertThatThrownBy(() -> authorizeService.authorizeBookingPayment(
                BOOKING_ID,
                "key",
                new AuthorizePaymentRequest(BANK_ID, PaymentMethod.COREBANK_TRANSFER),
                bank()))
                .isInstanceOf(PaymentProviderUnavailableException.class)
                .hasMessageContaining("finalization failed");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.COMPENSATION_REQUIRED);
        verify(idempotencyFailureMarker).markFailed(IDEMPOTENCY_KEY_ID);
    }

    private Booking booking(boolean instantBook) {
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setCustomerId(CUSTOMER_ID);
        booking.setListingId(UUID.fromString("77777777-7777-7777-7777-777777777777"));
        booking.setPickupDate(LocalDate.of(2026, 6, 1));
        booking.setReturnDate(LocalDate.of(2026, 6, 3));
        booking.setStatus(BookingStatus.HELD);
        booking.setHoldToken(UUID.fromString("88888888-8888-8888-8888-888888888888"));
        booking.setHoldExpiresAt(NOW.plusSeconds(900));
        booking.setPriceSnapshot("""
                {"totalAmount":1400000.00,"currency":"VND","extras":[]}
                """);
        booking.setPolicySnapshot("""
                {"instantBook":%s}
                """.formatted(instantBook));
        return booking;
    }

    private PaymentBank bank() {
        PaymentBank bank = new PaymentBank();
        bank.setId(BANK_ID);
        bank.setCode("COREBANK");
        bank.setPaymentMethod(PaymentMethod.COREBANK_TRANSFER);
        bank.setProvider(PaymentProviderType.COREBANK);
        bank.setActive(true);
        return bank;
    }

    private CustomerPaymentAccount customerPaymentAccount() {
        CustomerPaymentAccount account = new CustomerPaymentAccount();
        account.setUserId(CUSTOMER_ID);
        account.setProvider(PaymentProviderType.COREBANK);
        account.setProviderAccountId("payer-account-1");
        account.setActive(true);
        return account;
    }

    private List<AvailabilityCalendar> heldAvailabilityRows(Booking booking) {
        AvailabilityCalendar dayOne = new AvailabilityCalendar();
        dayOne.setListingId(booking.getListingId());
        dayOne.setAvailableDate(booking.getPickupDate());
        dayOne.setStatus(AvailabilityStatus.HOLD);
        dayOne.setBookingId(booking.getId());
        dayOne.setHoldToken(booking.getHoldToken());
        dayOne.setHoldExpiresAt(booking.getHoldExpiresAt());

        AvailabilityCalendar dayTwo = new AvailabilityCalendar();
        dayTwo.setListingId(booking.getListingId());
        dayTwo.setAvailableDate(booking.getPickupDate().plusDays(1));
        dayTwo.setStatus(AvailabilityStatus.HOLD);
        dayTwo.setBookingId(booking.getId());
        dayTwo.setHoldToken(booking.getHoldToken());
        dayTwo.setHoldExpiresAt(booking.getHoldExpiresAt());
        return List.of(dayOne, dayTwo);
    }
}
