package com.rentflow.payment.service;

import com.rentflow.audit.service.AuditLogService;
import com.rentflow.booking.service.BookingTimelineService;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.notification.service.AdminNotificationService;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.entity.PaymentTransactionStatus;
import com.rentflow.payment.entity.PaymentTransactionType;
import com.rentflow.payment.provider.PaymentProviderRouter;
import com.rentflow.payment.provider.VoidCommand;
import com.rentflow.payment.provider.VoidResult;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DefaultPaymentVoidRetryService implements PaymentVoidRetryService {

    private final BookingPaymentRepository bookingPaymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentProviderRouter paymentProviderRouter;
    private final CorrelationIdHelper correlationIdHelper;
    private final BookingTimelineService bookingTimelineService;
    private final AuditLogService auditLogService;
    private final OutboxService outboxService;
    private final AdminNotificationService adminNotificationService;
    private final Clock clock;
    private final int maxAttempts;
    private final long backoffSeconds;

    public DefaultPaymentVoidRetryService(
            BookingPaymentRepository bookingPaymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            PaymentProviderRouter paymentProviderRouter,
            CorrelationIdHelper correlationIdHelper,
            BookingTimelineService bookingTimelineService,
            AuditLogService auditLogService,
            OutboxService outboxService,
            AdminNotificationService adminNotificationService,
            Clock clock,
            @Value("${rentflow.scheduler.void-retry.max-attempts:10}") int maxAttempts,
            @Value("${rentflow.scheduler.void-retry.backoff-seconds:300}") long backoffSeconds) {
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentProviderRouter = paymentProviderRouter;
        this.correlationIdHelper = correlationIdHelper;
        this.bookingTimelineService = bookingTimelineService;
        this.auditLogService = auditLogService;
        this.outboxService = outboxService;
        this.adminNotificationService = adminNotificationService;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.backoffSeconds = backoffSeconds;
    }

    @Override
    @Transactional
    public int processBatch(int batchSize) {
        Instant now = clock.instant();
        List<BookingPayment> candidates = bookingPaymentRepository.findVoidRetryCandidatesForUpdate(
                now, maxAttempts, batchSize);
        int processed = 0;
        for (BookingPayment payment : candidates) {
            processed++;
            retrySingle(payment, now);
        }
        return processed;
    }

    private void retrySingle(BookingPayment payment, Instant now) {
        String correlationId = correlationIdHelper.getOrGenerate();
        String requestId = UUID.randomUUID().toString();
        PaymentTransaction tx = new PaymentTransaction();
        tx.setBookingPaymentId(payment.getId());
        tx.setBookingId(payment.getBookingId());
        tx.setType(PaymentTransactionType.VOID);
        tx.setStatus(PaymentTransactionStatus.PENDING);
        tx.setAmount(BigDecimal.ZERO);
        tx.setCurrency(payment.getCurrency());
        tx.setProvider(payment.getProvider());
        tx.setProviderRequestId(requestId);
        tx.setProviderRef(payment.getProviderHoldId());
        tx = paymentTransactionRepository.save(tx);

        try {
            VoidResult result = paymentProviderRouter.route(PaymentProviderType.COREBANK).voidAuthorization(new VoidCommand(
                    "rentflow:void-retry:" + payment.getId() + ":" + requestId,
                    payment.getProviderHoldId(),
                    correlationId,
                    requestId,
                    payment.getId().toString(),
                    correlationId));
            tx.setStatus(PaymentTransactionStatus.SUCCEEDED);
            tx.setProviderResponse(result.providerMetadataJson());
            paymentTransactionRepository.save(tx);

            payment.setVoidRetryRequired(false);
            payment.setVoidRetryNextAt(null);
            payment.setVoidRetryLastError(null);
            payment.setProviderStatus(result.providerStatus());
            payment.setProviderMetadata(result.providerMetadataJson());
            if (payment.getCapturedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                payment.setStatus(PaymentStatus.VOIDED);
            }
            bookingPaymentRepository.save(payment);

            String details = Jsons.toJson(Map.of("paymentId", payment.getId(), "retryCount", payment.getVoidRetryCount()));
            bookingTimelineService.append(payment.getBookingId(), "PAYMENT_VOID_RETRY_SUCCEEDED", null, "SYSTEM", details);
            auditLogService.record(null, "SYSTEM", "PAYMENT_VOID_RETRY", "BOOKING_PAYMENT", payment.getId(), "SUCCEEDED", details);
            outboxService.append("BOOKING_PAYMENT", payment.getId(), "PAYMENT_VOID_RETRY_RESOLVED", details);
            adminNotificationService.notifyPaymentVoidRetryResolved(
                    payment.getBookingId(),
                    payment.getId(),
                    payment.getVoidRetryCount() == null ? 0 : payment.getVoidRetryCount());
        } catch (RuntimeException e) {
            tx.setStatus(PaymentTransactionStatus.FAILED);
            tx.setProviderErrorCode("PAYMENT_PROVIDER_ERROR");
            tx.setProviderErrorMessage(e.getMessage());
            paymentTransactionRepository.save(tx);

            int nextCount = payment.getVoidRetryCount() == null ? 1 : payment.getVoidRetryCount() + 1;
            payment.setVoidRetryCount(nextCount);
            payment.setVoidRetryRequired(nextCount < maxAttempts);
            payment.setVoidRetryLastError(e.getMessage());
            payment.setVoidRetryNextAt(now.plusSeconds(backoffSeconds));
            payment.setProviderStatus("VOID_RETRY_REQUIRED");
            bookingPaymentRepository.save(payment);

            String details = Jsons.toJson(Map.of(
                    "paymentId", payment.getId(),
                    "retryCount", nextCount,
                    "retryRequired", payment.isVoidRetryRequired()));
            bookingTimelineService.append(payment.getBookingId(), "PAYMENT_VOID_RETRY_FAILED", null, "SYSTEM", details);
            auditLogService.record(null, "SYSTEM", "PAYMENT_VOID_RETRY", "BOOKING_PAYMENT", payment.getId(), "FAILED", details);
            if (!payment.isVoidRetryRequired()) {
                adminNotificationService.notifyPaymentVoidRetryFailedMaxAttempts(
                        payment.getBookingId(),
                        payment.getId(),
                        payment.getVoidRetryCount() == null ? 0 : payment.getVoidRetryCount());
            }
        }
    }
}
