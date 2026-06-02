package com.rentflow.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.auth.entity.Role;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.payment.dto.CapturePaymentRequest;
import com.rentflow.payment.dto.PaymentDetailResponse;
import com.rentflow.payment.dto.RefundPaymentRequest;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.entity.PaymentTransactionStatus;
import com.rentflow.payment.provider.CaptureResult;
import com.rentflow.payment.provider.PaymentProvider;
import com.rentflow.payment.provider.PaymentProviderRouter;
import com.rentflow.payment.provider.RefundResult;
import com.rentflow.payment.provider.VoidResult;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreBankMutationFinalizationSafetyTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID BOOKING_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PAYMENT_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID IDEMPOTENCY_KEY_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private BookingRepository bookingRepository;
    private BookingPaymentRepository bookingPaymentRepository;
    private PaymentTransactionRepository paymentTransactionRepository;
    private PaymentDetailResponseFactory paymentDetailResponseFactory;
    private SecurityContext securityContext;
    private IdempotencyService idempotencyService;
    private IdempotencyFailureMarker idempotencyFailureMarker;
    private PaymentProviderRouter paymentProviderRouter;
    private PaymentProvider paymentProvider;
    private CorrelationIdHelper correlationIdHelper;
    private PlatformTransactionManager transactionManager;
    private PaymentTransaction tx;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        bookingPaymentRepository = mock(BookingPaymentRepository.class);
        paymentTransactionRepository = mock(PaymentTransactionRepository.class);
        paymentDetailResponseFactory = mock(PaymentDetailResponseFactory.class);
        securityContext = mock(SecurityContext.class);
        idempotencyService = mock(IdempotencyService.class);
        idempotencyFailureMarker = mock(IdempotencyFailureMarker.class);
        paymentProviderRouter = mock(PaymentProviderRouter.class);
        paymentProvider = mock(PaymentProvider.class);
        correlationIdHelper = mock(CorrelationIdHelper.class);
        transactionManager = transactionManager();

        tx = new PaymentTransaction();
        tx.setId(UUID.fromString("44444444-4444-4444-8444-444444444444"));
        tx.setStatus(PaymentTransactionStatus.PENDING);

        when(securityContext.currentUserId()).thenReturn(ACTOR_ID);
        when(securityContext.hasRole(Role.ADMIN)).thenReturn(true);
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(eq(ACTOR_ID), any(IdempotencyScope.class), eq("key"), eq("hash")))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_KEY_ID));
        when(correlationIdHelper.getOrGenerate()).thenReturn("corr-1");
        when(paymentProviderRouter.route(PaymentProviderType.COREBANK)).thenReturn(paymentProvider);
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(tx.getId());
            }
            tx = saved;
            return saved;
        });
        when(paymentTransactionRepository.findByIdForUpdate(any())).thenReturn(Optional.of(tx));
        when(paymentTransactionRepository.findByBookingPaymentIdOrderByCreatedAtAsc(PAYMENT_ID)).thenReturn(List.of(tx));
        when(paymentDetailResponseFactory.create(any(), any(), any())).thenReturn(mock(PaymentDetailResponse.class));
    }

    @Test
    void captureProviderSuccessDoesNotOverwriteWhenLocalAmountAlreadyCaptured() {
        Booking booking = booking();
        BookingPayment preparePayment = authorizedPayment(BigDecimal.ZERO);
        BookingPayment finalPayment = authorizedPayment(new BigDecimal("1400000.00"));
        finalPayment.setStatus(PaymentStatus.CAPTURED);
        when(bookingPaymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(preparePayment));
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingPaymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(preparePayment), Optional.of(finalPayment));
        when(paymentProvider.capture(any())).thenReturn(new CaptureResult("CAPTURED", "journal-1", "{\"status\":\"CAPTURED\"}"));

        CoreBankCaptureService service = new CoreBankCaptureService(
                bookingRepository,
                bookingPaymentRepository,
                paymentTransactionRepository,
                paymentDetailResponseFactory,
                securityContext,
                idempotencyService,
                idempotencyFailureMarker,
                paymentProviderRouter,
                correlationIdHelper,
                new ObjectMapper(),
                transactionManager);

        assertThatThrownBy(() -> service.capture(PAYMENT_ID, "key", new CapturePaymentRequest(new BigDecimal("1400000.00"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_FINALIZATION_UNSAFE");

        assertThat(finalPayment.getCapturedAmount()).isEqualByComparingTo("1400000.00");
        assertThat(tx.getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(tx.getProviderErrorCode()).isEqualTo("PAYMENT_FINALIZATION_UNSAFE");
        assertThat(tx.getProviderJournalId()).isEqualTo("journal-1");
        verify(idempotencyFailureMarker).markFailed(IDEMPOTENCY_KEY_ID);
    }

    @Test
    void refundProviderSuccessDoesNotOverwriteWhenLocalAmountAlreadyRefunded() {
        Booking booking = booking();
        BookingPayment preparePayment = capturedPayment(BigDecimal.ZERO);
        BookingPayment finalPayment = capturedPayment(new BigDecimal("1400000.00"));
        finalPayment.setStatus(PaymentStatus.REFUNDED);
        when(bookingPaymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(preparePayment));
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingPaymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(preparePayment), Optional.of(finalPayment));
        when(paymentProvider.refund(any())).thenReturn(new RefundResult("REFUNDED", "journal-2", "{\"status\":\"REFUNDED\"}"));

        CoreBankRefundService service = new CoreBankRefundService(
                bookingRepository,
                bookingPaymentRepository,
                paymentTransactionRepository,
                paymentDetailResponseFactory,
                securityContext,
                idempotencyService,
                idempotencyFailureMarker,
                paymentProviderRouter,
                correlationIdHelper,
                new ObjectMapper(),
                transactionManager);

        assertThatThrownBy(() -> service.refund(PAYMENT_ID, "key", new RefundPaymentRequest(new BigDecimal("1400000.00"), "test")))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_FINALIZATION_UNSAFE");

        assertThat(finalPayment.getRefundedAmount()).isEqualByComparingTo("1400000.00");
        assertThat(tx.getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(tx.getProviderErrorCode()).isEqualTo("PAYMENT_FINALIZATION_UNSAFE");
        assertThat(tx.getProviderJournalId()).isEqualTo("journal-2");
        verify(idempotencyFailureMarker).markFailed(IDEMPOTENCY_KEY_ID);
    }

    @Test
    void voidProviderSuccessDoesNotOverwriteWhenLocalPaymentWasCaptured() {
        Booking booking = booking();
        BookingPayment preparePayment = authorizedPayment(BigDecimal.ZERO);
        BookingPayment finalPayment = authorizedPayment(new BigDecimal("100000.00"));
        finalPayment.setStatus(PaymentStatus.CAPTURED);
        when(bookingPaymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(preparePayment));
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingPaymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(preparePayment), Optional.of(finalPayment));
        when(paymentProvider.voidAuthorization(any())).thenReturn(new VoidResult("VOIDED", "{\"status\":\"VOIDED\"}"));

        CoreBankVoidService service = new CoreBankVoidService(
                bookingRepository,
                bookingPaymentRepository,
                paymentTransactionRepository,
                paymentDetailResponseFactory,
                securityContext,
                idempotencyService,
                idempotencyFailureMarker,
                paymentProviderRouter,
                correlationIdHelper,
                new ObjectMapper(),
                transactionManager);

        assertThatThrownBy(() -> service.voidAuthorization(PAYMENT_ID, "key"))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_FINALIZATION_UNSAFE");

        assertThat(finalPayment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(finalPayment.getCapturedAmount()).isEqualByComparingTo("100000.00");
        assertThat(tx.getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(tx.getProviderErrorCode()).isEqualTo("PAYMENT_FINALIZATION_UNSAFE");
        verify(idempotencyFailureMarker).markFailed(IDEMPOTENCY_KEY_ID);
    }

    private Booking booking() {
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setCustomerId(ACTOR_ID);
        booking.setHostId(UUID.fromString("55555555-5555-4555-8555-555555555555"));
        booking.setListingId(UUID.fromString("66666666-6666-4666-8666-666666666666"));
        booking.setPickupDate(LocalDate.of(2026, 6, 1));
        booking.setReturnDate(LocalDate.of(2026, 6, 3));
        return booking;
    }

    private BookingPayment authorizedPayment(BigDecimal capturedAmount) {
        BookingPayment payment = basePayment();
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setCapturedAmount(capturedAmount);
        return payment;
    }

    private BookingPayment capturedPayment(BigDecimal refundedAmount) {
        BookingPayment payment = basePayment();
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCapturedAmount(new BigDecimal("1400000.00"));
        payment.setRefundedAmount(refundedAmount);
        return payment;
    }

    private BookingPayment basePayment() {
        BookingPayment payment = new BookingPayment();
        payment.setId(PAYMENT_ID);
        payment.setBookingId(BOOKING_ID);
        payment.setPaymentMethod(PaymentMethod.COREBANK_TRANSFER);
        payment.setProvider(PaymentProviderType.COREBANK);
        payment.setAuthorizedAmount(new BigDecimal("1400000.00"));
        payment.setCapturedAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setCurrency("VND");
        payment.setProviderPaymentOrderId("payment-order-1");
        payment.setProviderHoldId("hold-1");
        return payment;
    }

    private PlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
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
    }
}
