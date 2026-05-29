package com.rentflow.payment.service;

import com.rentflow.audit.service.AuditLogService;
import com.rentflow.booking.service.BookingTimelineService;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.PaymentNotFoundException;
import com.rentflow.notification.service.AdminNotificationService;
import com.rentflow.outbox.service.OutboxService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;

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
            PlatformTransactionManager transactionManager,
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
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public int processBatch(int batchSize) {
        Instant now = clock.instant();
        List<UUID> candidates = transactionTemplate.execute(status -> bookingPaymentRepository
                .findVoidRetryCandidatesForUpdate(now, maxAttempts, batchSize)
                .stream()
                .map(BookingPayment::getId)
                .toList());

        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }

        int processed = 0;
        for (UUID paymentId : candidates) {
            if (retrySingle(paymentId, now)) {
                processed++;
            }
        }
        return processed;
    }

    private boolean retrySingle(UUID paymentId, Instant now) {
        PreparedRetryContext prepared = transactionTemplate.execute(status -> prepareRetry(paymentId));
        if (prepared == null) {
            return false;
        }

        try {
            VoidResult result = paymentProviderRouter.route(PaymentProviderType.COREBANK)
                    .voidAuthorization(prepared.command());
            transactionTemplate.executeWithoutResult(status -> finalizeRetrySuccess(prepared, result));
            return true;
        } catch (RuntimeException e) {
            RetryFailure failure = failureFrom(e);
            transactionTemplate.executeWithoutResult(status -> finalizeRetryFailure(prepared, now, failure));
            return true;
        }
    }

    private PreparedRetryContext prepareRetry(UUID paymentId) {
        BookingPayment payment = bookingPaymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId.toString()));
        if (!isRetryEligible(payment)) {
            return null;
        }

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

        return new PreparedRetryContext(
                payment.getId(),
                payment.getBookingId(),
                tx.getId(),
                new VoidCommand(
                        "rentflow:void-retry:" + payment.getId() + ":" + requestId,
                        payment.getProviderHoldId(),
                        correlationId,
                        requestId,
                        payment.getId().toString(),
                        correlationId));
    }

    private void finalizeRetrySuccess(PreparedRetryContext prepared, VoidResult result) {
        BookingPayment payment = bookingPaymentRepository.findByIdForUpdate(prepared.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException(prepared.paymentId().toString()));
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new PaymentNotFoundException(prepared.paymentId().toString()));
        if (!isRetryEligible(payment)) {
            markUnsafe(tx, "CoreBank void retry succeeded but local finalization was no longer safe");
            return;
        }

        tx.setStatus(PaymentTransactionStatus.SUCCEEDED);
        tx.setProviderResponse(result.providerMetadataJson());
        tx.setProviderErrorCode(null);
        tx.setProviderErrorMessage(null);
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
    }

    private void finalizeRetryFailure(PreparedRetryContext prepared, Instant now, RetryFailure failure) {
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new PaymentNotFoundException(prepared.paymentId().toString()));
        tx.setStatus(PaymentTransactionStatus.FAILED);
        tx.setProviderErrorCode(failure.errorCode());
        tx.setProviderErrorMessage(failure.errorMessage());
        paymentTransactionRepository.save(tx);

        BookingPayment payment = bookingPaymentRepository.findByIdForUpdate(prepared.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException(prepared.paymentId().toString()));
        if (!isRetryEligible(payment)) {
            return;
        }

        int nextCount = payment.getVoidRetryCount() == null ? 1 : payment.getVoidRetryCount() + 1;
        payment.setVoidRetryCount(nextCount);
        payment.setVoidRetryRequired(nextCount < maxAttempts);
        payment.setVoidRetryLastError(failure.errorMessage());
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

    private boolean isRetryEligible(BookingPayment payment) {
        return payment.getProvider() == PaymentProviderType.COREBANK
                && payment.isVoidRetryRequired()
                && payment.getProviderHoldId() != null
                && !payment.getProviderHoldId().isBlank()
                && (payment.getVoidRetryCount() == null || payment.getVoidRetryCount() < maxAttempts);
    }

    private RetryFailure failureFrom(RuntimeException e) {
        if (e instanceof org.springframework.dao.TransientDataAccessException) {
            return new RetryFailure("PAYMENT_PROVIDER_ERROR", e.getMessage());
        }
        if (e instanceof org.springframework.web.client.RestClientException && e.getMessage() == null) {
            return new RetryFailure("PAYMENT_PROVIDER_ERROR", "Provider call failed");
        }
        if (e instanceof com.rentflow.common.exception.PaymentProviderUnavailableException) {
            return new RetryFailure("PAYMENT_PROVIDER_UNAVAILABLE", e.getMessage());
        }
        return new RetryFailure("PAYMENT_PROVIDER_ERROR", e.getMessage());
    }

    private void markUnsafe(PaymentTransaction tx, String errorMessage) {
        tx.setStatus(PaymentTransactionStatus.FAILED);
        tx.setProviderErrorCode("PAYMENT_FINALIZATION_UNSAFE");
        tx.setProviderErrorMessage(errorMessage);
        paymentTransactionRepository.save(tx);
    }

    private record PreparedRetryContext(
            UUID paymentId,
            UUID bookingId,
            UUID transactionId,
            VoidCommand command
    ) {
    }

    private record RetryFailure(
            String errorCode,
            String errorMessage
    ) {
    }
}
