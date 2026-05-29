package com.rentflow.payment.service;

import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.PaymentException;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.entity.PaymentTransactionStatus;
import com.rentflow.payment.provider.CaptureResult;
import com.rentflow.payment.provider.PaymentProvider;
import com.rentflow.payment.provider.PaymentProviderRouter;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripPaymentCaptureServiceTest {

    private static final UUID BOOKING_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PAYMENT_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private BookingPaymentRepository bookingPaymentRepository;
    private PaymentTransactionRepository paymentTransactionRepository;
    private PaymentProviderRouter paymentProviderRouter;
    private PaymentProvider paymentProvider;
    private CorrelationIdHelper correlationIdHelper;
    private TripPaymentCaptureService service;

    @BeforeEach
    void setUp() {
        bookingPaymentRepository = mock(BookingPaymentRepository.class);
        paymentTransactionRepository = mock(PaymentTransactionRepository.class);
        paymentProviderRouter = mock(PaymentProviderRouter.class);
        paymentProvider = mock(PaymentProvider.class);
        correlationIdHelper = mock(CorrelationIdHelper.class);

        when(correlationIdHelper.getOrGenerate()).thenReturn("corr-1");
        when(paymentProviderRouter.route(PaymentProviderType.COREBANK)).thenReturn(paymentProvider);
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

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

        service = new TripPaymentCaptureService(
                bookingPaymentRepository,
                paymentTransactionRepository,
                paymentProviderRouter,
                correlationIdHelper,
                transactionManager);
    }

    @Test
    void captureSuccessUpdatesPaymentAndTransaction() {
        BookingPayment payment = authorizedPayment(BigDecimal.ZERO);
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID());

        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID))
                .thenReturn(Optional.of(payment), Optional.of(payment));
        when(paymentTransactionRepository.findByIdForUpdate(any())).thenReturn(Optional.of(tx));
        when(paymentProvider.capture(any())).thenReturn(new CaptureResult("CAPTURED", "journal-1", "{\"status\":\"CAPTURED\"}"));

        service.captureRemainingForBooking(BOOKING_ID);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getCapturedAmount()).isEqualByComparingTo("1400000.00");
        assertThat(tx.getStatus()).isEqualTo(PaymentTransactionStatus.SUCCEEDED);
        assertThat(tx.getProviderJournalId()).isEqualTo("journal-1");
    }

    @Test
    void providerUnavailableMarksTransactionFailedAndThrowsPaymentException() {
        BookingPayment payment = authorizedPayment(BigDecimal.ZERO);
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID());

        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID))
                .thenReturn(Optional.of(payment));
        when(paymentTransactionRepository.findByIdForUpdate(any())).thenReturn(Optional.of(tx));
        when(paymentProvider.capture(any())).thenThrow(new com.rentflow.common.exception.PaymentProviderUnavailableException("provider down"));

        assertThatThrownBy(() -> service.captureRemainingForBooking(BOOKING_ID))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("check-out");

        assertThat(tx.getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(tx.getProviderErrorCode()).isEqualTo("PAYMENT_PROVIDER_UNAVAILABLE");
    }

    @Test
    void providerSuccessDoesNotOverwriteWhenPaymentStateDrifts() {
        BookingPayment preparePayment = authorizedPayment(BigDecimal.ZERO);
        BookingPayment finalPayment = authorizedPayment(BigDecimal.ZERO);
        finalPayment.setStatus(PaymentStatus.CAPTURED);
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID());

        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID))
                .thenReturn(Optional.of(preparePayment), Optional.of(finalPayment));
        when(paymentTransactionRepository.findByIdForUpdate(any())).thenReturn(Optional.of(tx));
        when(paymentProvider.capture(any())).thenReturn(new CaptureResult("CAPTURED", "journal-1", "{\"status\":\"CAPTURED\"}"));

        assertThatThrownBy(() -> service.captureRemainingForBooking(BOOKING_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_FINALIZATION_UNSAFE");

        assertThat(finalPayment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(finalPayment.getCapturedAmount()).isEqualByComparingTo("0.00");
        assertThat(tx.getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(tx.getProviderErrorCode()).isEqualTo("PAYMENT_FINALIZATION_UNSAFE");
    }

    @Test
    void nonEligiblePaymentReturnsWithoutProviderCall() {
        BookingPayment payment = authorizedPayment(new BigDecimal("1400000.00"));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(payment));

        service.captureRemainingForBooking(BOOKING_ID);

        verify(paymentProvider, never()).capture(any());
        verify(paymentTransactionRepository, never()).save(any(PaymentTransaction.class));
    }

    private BookingPayment authorizedPayment(BigDecimal capturedAmount) {
        BookingPayment payment = new BookingPayment();
        payment.setId(PAYMENT_ID);
        payment.setBookingId(BOOKING_ID);
        payment.setProvider(PaymentProviderType.COREBANK);
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthorizedAmount(new BigDecimal("1400000.00"));
        payment.setCapturedAmount(capturedAmount);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setCurrency("VND");
        payment.setProviderPaymentOrderId("payment-order-1");
        return payment;
    }
}
