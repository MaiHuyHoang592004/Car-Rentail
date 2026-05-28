package com.rentflow.scheduler;

import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.booking.service.AvailabilityReserver;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.PaymentProviderUnavailableException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class ExpireHostApprovalsProcessor {

    private final BookingRepository bookingRepository;
    private final BookingPaymentRepository bookingPaymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final AvailabilityReserver availabilityReserver;
    private final IdempotencyService idempotencyService;
    private final IdempotencyFailureMarker idempotencyFailureMarker;
    private final PaymentProviderRouter paymentProviderRouter;
    private final CorrelationIdHelper correlationIdHelper;
    private final Clock clock;
    private final long backoffSeconds;

    public ExpireHostApprovalsProcessor(
            BookingRepository bookingRepository,
            BookingPaymentRepository bookingPaymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            AvailabilityReserver availabilityReserver,
            IdempotencyService idempotencyService,
            IdempotencyFailureMarker idempotencyFailureMarker,
            PaymentProviderRouter paymentProviderRouter,
            CorrelationIdHelper correlationIdHelper,
            Clock clock,
            @Value("${rentflow.scheduler.void-retry.backoff-seconds:300}") long backoffSeconds) {
        this.bookingRepository = bookingRepository;
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.availabilityReserver = availabilityReserver;
        this.idempotencyService = idempotencyService;
        this.idempotencyFailureMarker = idempotencyFailureMarker;
        this.paymentProviderRouter = paymentProviderRouter;
        this.correlationIdHelper = correlationIdHelper;
        this.clock = clock;
        this.backoffSeconds = backoffSeconds;
    }

    @Transactional
    public int processBatch(int batchSize) {
        Instant now = clock.instant();
        List<Booking> candidates = bookingRepository.findExpiredHostApprovalCandidatesForUpdate(now, batchSize);
        int processed = 0;
        for (Booking booking : candidates) {
            if (booking.getStatus() != BookingStatus.PENDING_HOST_APPROVAL
                    || booking.getHostApprovalExpiresAt() == null
                    || !booking.getHostApprovalExpiresAt().isBefore(now)) {
                continue;
            }
            UUID idempotencyKeyId = null;
            try {
                String key = "host-approval-expire:" + booking.getId();
                String requestHash = idempotencyService.computeHash(
                        new HostApprovalExpiryHashInput(booking.getId(), booking.getHostApprovalExpiresAt()));
                IdempotencyResolution resolution = idempotencyService.resolve(
                        booking.getHostId(),
                        IdempotencyScope.HOST_EXPIRE_BOOKING_APPROVAL,
                        key,
                        requestHash);
                if (resolution instanceof IdempotencyResolution.Replay) {
                    continue;
                }
                idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
                expireSingle(booking, key, idempotencyKeyId);
                processed++;
            } catch (RuntimeException e) {
                if (idempotencyKeyId != null) {
                    idempotencyFailureMarker.markFailed(idempotencyKeyId);
                }
            }
        }
        return processed;
    }

    private void expireSingle(Booking booking, String idempotencyKey, UUID idempotencyKeyId) {
        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(booking.getId())
                .orElseThrow(() -> new BusinessRuleException("PAYMENT_NOT_FOUND", "Payment not found for booking"));
        validatePayment(payment);

        List<AvailabilityCalendar> availabilityRows = availabilityReserver.lockForBooking(booking);
        validateHeldAvailabilityRows(availabilityRows, booking);

        try {
            VoidResult voidResult = voidAuthorization(payment, idempotencyKey, idempotencyKeyId);
            payment.setStatus(PaymentStatus.VOIDED);
            payment.setProviderStatus(voidResult.providerStatus());
            payment.setProviderMetadata(voidResult.providerMetadataJson());
            payment.setVoidRetryRequired(false);
            payment.setVoidRetryNextAt(null);
            payment.setVoidRetryLastError(null);
            bookingPaymentRepository.save(payment);

            booking.setStatus(BookingStatus.EXPIRED);
            booking.setHoldToken(null);
            booking.setHoldExpiresAt(null);
            booking.setHostApprovalExpiresAt(null);
            bookingRepository.save(booking);

            availabilityRows.forEach(row -> {
                row.setStatus(AvailabilityStatus.FREE);
                row.setBookingId(null);
                row.setHoldToken(null);
                row.setHoldExpiresAt(null);
            });
            availabilityReserver.saveRows(availabilityRows);
            idempotencyService.complete(idempotencyKeyId, 200, "{\"status\":\"EXPIRED\"}");
        } catch (RuntimeException e) {
            markVoidRetryRequired(payment, e);
            throw e;
        }
    }

    private VoidResult voidAuthorization(
            BookingPayment payment,
            String schedulerIdempotencyKey,
            UUID idempotencyKeyId) {
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
        tx.setIdempotencyKeyId(idempotencyKeyId);
        tx = paymentTransactionRepository.save(tx);

        try {
            VoidResult result = paymentProviderRouter.route(PaymentProviderType.COREBANK).voidAuthorization(new VoidCommand(
                    "rentflow:host-approval-timeout:void:" + payment.getId() + ":" + schedulerIdempotencyKey,
                    payment.getProviderHoldId(),
                    correlationId,
                    requestId,
                    payment.getId().toString(),
                    correlationId));
            tx.setStatus(PaymentTransactionStatus.SUCCEEDED);
            tx.setProviderResponse(result.providerMetadataJson());
            paymentTransactionRepository.save(tx);
            return result;
        } catch (PaymentProviderUnavailableException e) {
            tx.setStatus(PaymentTransactionStatus.FAILED);
            tx.setProviderErrorCode("PAYMENT_PROVIDER_UNAVAILABLE");
            tx.setProviderErrorMessage(e.getMessage());
            paymentTransactionRepository.save(tx);
            throw e;
        } catch (RuntimeException e) {
            tx.setStatus(PaymentTransactionStatus.FAILED);
            tx.setProviderErrorCode("PAYMENT_PROVIDER_ERROR");
            tx.setProviderErrorMessage(e.getMessage());
            paymentTransactionRepository.save(tx);
            throw e;
        }
    }

    private void validatePayment(BookingPayment payment) {
        if (payment.getProvider() != PaymentProviderType.COREBANK) {
            throw new BusinessRuleException("PAYMENT_PROVIDER_UNSUPPORTED", "Host approval expiry supports CoreBank payment only");
        }
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new BusinessRuleException("PAYMENT_INVALID_STATUS", "Payment must be AUTHORIZED");
        }
        if (payment.getCapturedAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessRuleException("PAYMENT_VOID_CAPTURED_NOT_ALLOWED", "Cannot void a payment with captured amount");
        }
        if (payment.getProviderHoldId() == null || payment.getProviderHoldId().isBlank()) {
            throw new BusinessRuleException("PAYMENT_PROVIDER_REFERENCE_MISSING", "Payment is missing provider hold reference");
        }
    }

    private void validateHeldAvailabilityRows(List<AvailabilityCalendar> rows, Booking booking) {
        long expectedDays = java.time.temporal.ChronoUnit.DAYS.between(booking.getPickupDate(), booking.getReturnDate());
        boolean invalid = rows.size() != expectedDays
                || rows.stream().anyMatch(row -> row.getStatus() != AvailabilityStatus.HOLD)
                || rows.stream().anyMatch(row -> !booking.getId().equals(row.getBookingId()));
        if (invalid) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Availability is no longer reserved for this booking");
        }
    }

    private void markVoidRetryRequired(BookingPayment payment, RuntimeException e) {
        int nextCount = payment.getVoidRetryCount() == null ? 1 : payment.getVoidRetryCount() + 1;
        payment.setVoidRetryCount(nextCount);
        payment.setVoidRetryRequired(true);
        payment.setVoidRetryLastError(e.getMessage());
        payment.setVoidRetryNextAt(clock.instant().plusSeconds(backoffSeconds));
        payment.setProviderStatus("VOID_RETRY_REQUIRED");
        bookingPaymentRepository.save(payment);
    }

    private record HostApprovalExpiryHashInput(UUID bookingId, Instant hostApprovalExpiresAt) {
    }
}
