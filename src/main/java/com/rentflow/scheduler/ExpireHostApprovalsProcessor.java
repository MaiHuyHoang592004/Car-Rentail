package com.rentflow.scheduler;

import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.booking.service.AvailabilityReserver;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.PaymentNotFoundException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;

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
            PlatformTransactionManager transactionManager,
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
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public int processBatch(int batchSize) {
        Instant now = clock.instant();
        List<ExpiredHostApprovalCandidate> candidates = transactionTemplate.execute(status -> bookingRepository
                .findExpiredHostApprovalCandidatesForUpdate(now, batchSize)
                .stream()
                .filter(booking -> isExpiredPendingApproval(booking, now))
                .map(booking -> new ExpiredHostApprovalCandidate(
                        booking.getId(),
                        booking.getHostId(),
                        booking.getHostApprovalExpiresAt()))
                .toList());

        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }

        int processed = 0;
        for (ExpiredHostApprovalCandidate candidate : candidates) {
            UUID idempotencyKeyId = null;
            try {
                String key = "host-approval-expire:" + candidate.bookingId();
                String requestHash = idempotencyService.computeHash(
                        new HostApprovalExpiryHashInput(candidate.bookingId(), candidate.hostApprovalExpiresAt()));
                IdempotencyResolution resolution = idempotencyService.resolve(
                        candidate.hostId(),
                        IdempotencyScope.HOST_EXPIRE_BOOKING_APPROVAL,
                        key,
                        requestHash);
                if (resolution instanceof IdempotencyResolution.Replay) {
                    continue;
                }

                idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
                UUID resolvedIdempotencyKeyId = idempotencyKeyId;
                PreparedExpiryContext prepared = transactionTemplate.execute(status ->
                        prepareExpiry(candidate.bookingId(), key, resolvedIdempotencyKeyId));
                if (prepared == null) {
                    continue;
                }

                VoidResult voidResult;
                try {
                    voidResult = paymentProviderRouter.route(PaymentProviderType.COREBANK)
                            .voidAuthorization(prepared.command());
                } catch (RuntimeException e) {
                    String errorCode = e instanceof com.rentflow.common.exception.PaymentProviderUnavailableException
                            ? "PAYMENT_PROVIDER_UNAVAILABLE"
                            : "PAYMENT_PROVIDER_ERROR";
                    transactionTemplate.executeWithoutResult(status ->
                            markProviderFailureAndRetry(prepared, errorCode, e.getMessage()));
                    throw e;
                }
                transactionTemplate.executeWithoutResult(status ->
                        finalizeExpiry(prepared, voidResult, resolvedIdempotencyKeyId));
                processed++;
            } catch (RuntimeException e) {
                if (idempotencyKeyId != null) {
                    idempotencyFailureMarker.markFailed(idempotencyKeyId);
                }
            }
        }
        return processed;
    }

    private PreparedExpiryContext prepareExpiry(UUID bookingId, String schedulerIdempotencyKey, UUID idempotencyKeyId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        if (!isExpiredPendingApproval(booking, clock.instant())) {
            return null;
        }

        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(booking.getId())
                .orElseThrow(() -> new BusinessRuleException("PAYMENT_NOT_FOUND", "Payment not found for booking"));
        validatePayment(payment);

        List<AvailabilityCalendar> availabilityRows = availabilityReserver.lockForBooking(booking);
        validateHeldAvailabilityRows(availabilityRows, booking);

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

        return new PreparedExpiryContext(
                booking.getId(),
                tx.getId(),
                new VoidCommand(
                        "rentflow:host-approval-timeout:void:" + payment.getId() + ":" + schedulerIdempotencyKey,
                        payment.getProviderHoldId(),
                        correlationId,
                        requestId,
                        payment.getId().toString(),
                        correlationId));
    }

    private void finalizeExpiry(
            PreparedExpiryContext prepared,
            VoidResult voidResult,
            UUID idempotencyKeyId) {
        Booking booking = bookingRepository.findByIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(prepared.bookingId().toString()));
        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new PaymentNotFoundException(prepared.bookingId().toString()));
        List<AvailabilityCalendar> availabilityRows = availabilityReserver.lockForBooking(booking);
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new PaymentNotFoundException(prepared.bookingId().toString()));

        validateExpiryFinalizationState(booking, payment, availabilityRows, tx);

        payment.setStatus(PaymentStatus.VOIDED);
        payment.setProviderStatus(voidResult.providerStatus());
        payment.setProviderMetadata(voidResult.providerMetadataJson());
        payment.setVoidRetryRequired(false);
        payment.setVoidRetryNextAt(null);
        payment.setVoidRetryLastError(null);
        bookingPaymentRepository.save(payment);

        tx.setStatus(PaymentTransactionStatus.SUCCEEDED);
        tx.setProviderResponse(voidResult.providerMetadataJson());
        tx.setProviderErrorCode(null);
        tx.setProviderErrorMessage(null);
        paymentTransactionRepository.save(tx);

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
    }

    private void validateExpiryFinalizationState(
            Booking booking,
            BookingPayment payment,
            List<AvailabilityCalendar> availabilityRows,
            PaymentTransaction tx) {
        try {
            if (!isExpiredPendingApproval(booking, clock.instant())) {
                throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking is no longer an expired pending host approval");
            }
            validatePayment(payment);
            validateHeldAvailabilityRows(availabilityRows, booking);
        } catch (BusinessRuleException e) {
            markUnsafe(tx, "CoreBank void succeeded but host approval expiry finalization was no longer safe");
            throw new BusinessRuleException(
                    "PAYMENT_FINALIZATION_UNSAFE",
                    "Payment provider void succeeded but booking expiry could not be finalized safely");
        }
    }

    private boolean isExpiredPendingApproval(Booking booking, Instant now) {
        return booking.getStatus() == BookingStatus.PENDING_HOST_APPROVAL
                && booking.getHostApprovalExpiresAt() != null
                && booking.getHostApprovalExpiresAt().isBefore(now);
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

    private void markProviderFailureAndRetry(
            PreparedExpiryContext prepared,
            String errorCode,
            String errorMessage) {
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new PaymentNotFoundException(prepared.bookingId().toString()));
        tx.setStatus(PaymentTransactionStatus.FAILED);
        tx.setProviderErrorCode(errorCode);
        tx.setProviderErrorMessage(errorMessage);
        paymentTransactionRepository.save(tx);

        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new PaymentNotFoundException(prepared.bookingId().toString()));
        markVoidRetryRequired(payment, errorMessage);
    }

    private void markVoidRetryRequired(BookingPayment payment, String errorMessage) {
        int nextCount = payment.getVoidRetryCount() == null ? 1 : payment.getVoidRetryCount() + 1;
        payment.setVoidRetryCount(nextCount);
        payment.setVoidRetryRequired(true);
        payment.setVoidRetryLastError(errorMessage);
        payment.setVoidRetryNextAt(clock.instant().plusSeconds(backoffSeconds));
        payment.setProviderStatus("VOID_RETRY_REQUIRED");
        bookingPaymentRepository.save(payment);
    }

    private void markUnsafe(PaymentTransaction tx, String errorMessage) {
        tx.setStatus(PaymentTransactionStatus.FAILED);
        tx.setProviderErrorCode("PAYMENT_FINALIZATION_UNSAFE");
        tx.setProviderErrorMessage(errorMessage);
        paymentTransactionRepository.save(tx);
    }

    private record HostApprovalExpiryHashInput(UUID bookingId, Instant hostApprovalExpiresAt) {
    }

    private record ExpiredHostApprovalCandidate(
            UUID bookingId,
            UUID hostId,
            Instant hostApprovalExpiresAt
    ) {
    }

    private record PreparedExpiryContext(
            UUID bookingId,
            UUID transactionId,
            VoidCommand command
    ) {
    }
}
