package com.rentflow.payment.service;

import com.rentflow.audit.service.AuditLogService;
import com.rentflow.booking.service.BookingTimelineService;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.outbox.service.OutboxService;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPaymentVoidRetryServiceTest {

    @Mock private BookingPaymentRepository bookingPaymentRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private PaymentProviderRouter paymentProviderRouter;
    @Mock private PaymentProvider paymentProvider;
    @Mock private CorrelationIdHelper correlationIdHelper;
    @Mock private BookingTimelineService bookingTimelineService;
    @Mock private AuditLogService auditLogService;
    @Mock private OutboxService outboxService;

    private DefaultPaymentVoidRetryService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneOffset.UTC);
        service = new DefaultPaymentVoidRetryService(
                bookingPaymentRepository,
                paymentTransactionRepository,
                paymentProviderRouter,
                correlationIdHelper,
                bookingTimelineService,
                auditLogService,
                outboxService,
                clock,
                3,
                120);
        lenient().when(correlationIdHelper.getOrGenerate()).thenReturn("corr-1");
        lenient().when(paymentProviderRouter.route(PaymentProviderType.COREBANK)).thenReturn(paymentProvider);
        lenient().when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void retrySuccessClearsRetryFlags() {
        BookingPayment payment = candidate();
        when(bookingPaymentRepository.findVoidRetryCandidatesForUpdate(any(), any(Integer.class), any(Integer.class)))
                .thenReturn(List.of(payment));
        when(paymentProvider.voidAuthorization(any())).thenReturn(new VoidResult("VOIDED", "{\"status\":\"VOIDED\"}"));

        int processed = service.processBatch(10);

        assertThat(processed).isEqualTo(1);
        assertThat(payment.isVoidRetryRequired()).isFalse();
        assertThat(payment.getVoidRetryNextAt()).isNull();
        assertThat(payment.getVoidRetryLastError()).isNull();
        verify(outboxService).append(any(), any(), any(), any());
    }

    @Test
    void retryFailureIncrementsCounterAndSchedulesNextAttempt() {
        BookingPayment payment = candidate();
        when(bookingPaymentRepository.findVoidRetryCandidatesForUpdate(any(), any(Integer.class), any(Integer.class)))
                .thenReturn(List.of(payment));
        doThrow(new RuntimeException("provider down")).when(paymentProvider).voidAuthorization(any());

        int processed = service.processBatch(10);

        assertThat(processed).isEqualTo(1);
        assertThat(payment.isVoidRetryRequired()).isTrue();
        assertThat(payment.getVoidRetryCount()).isEqualTo(1);
        assertThat(payment.getVoidRetryNextAt()).isNotNull();
        verify(auditLogService).record(any(), any(), any(), any(), any(), any(), any());
    }

    private BookingPayment candidate() {
        BookingPayment payment = new BookingPayment();
        payment.setId(UUID.fromString("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa"));
        payment.setBookingId(UUID.fromString("bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb"));
        payment.setProvider(PaymentProviderType.COREBANK);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCurrency("VND");
        payment.setCapturedAmount(new BigDecimal("100000.00"));
        payment.setProviderHoldId("hold-1");
        payment.setVoidRetryRequired(true);
        payment.setVoidRetryCount(0);
        return payment;
    }
}
