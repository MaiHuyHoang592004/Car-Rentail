package com.rentflow.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.auth.entity.Role;
import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.mapper.BookingMapper;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.PaymentNotFoundException;
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
import com.rentflow.payment.entity.PaymentTransactionStatus;
import com.rentflow.payment.entity.PaymentTransactionType;
import com.rentflow.payment.provider.PaymentProviderRouter;
import com.rentflow.payment.provider.VoidCommand;
import com.rentflow.payment.provider.VoidResult;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class HostBookingApprovalService {

    private final BookingRepository bookingRepository;
    private final BookingPaymentRepository bookingPaymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final AvailabilityReserver availabilityReserver;
    private final BookingMapper bookingMapper;
    private final SecurityContext securityContext;
    private final IdempotencyService idempotencyService;
    private final IdempotencyFailureMarker idempotencyFailureMarker;
    private final PaymentProviderRouter paymentProviderRouter;
    private final CorrelationIdHelper correlationIdHelper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public HostBookingApprovalService(
            BookingRepository bookingRepository,
            BookingPaymentRepository bookingPaymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            AvailabilityReserver availabilityReserver,
            BookingMapper bookingMapper,
            SecurityContext securityContext,
            IdempotencyService idempotencyService,
            IdempotencyFailureMarker idempotencyFailureMarker,
            PaymentProviderRouter paymentProviderRouter,
            CorrelationIdHelper correlationIdHelper,
            ObjectMapper objectMapper,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.availabilityReserver = availabilityReserver;
        this.bookingMapper = bookingMapper;
        this.securityContext = securityContext;
        this.idempotencyService = idempotencyService;
        this.idempotencyFailureMarker = idempotencyFailureMarker;
        this.paymentProviderRouter = paymentProviderRouter;
        this.correlationIdHelper = correlationIdHelper;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingSummaryResponse> listHostBookings(BookingStatus status, UUID listingId, Pageable pageable) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);

        Page<Booking> bookings;
        if (listingId != null && status != null) {
            bookings = bookingRepository.findByHostIdAndListingIdAndStatusOrderByCreatedAtDesc(
                    hostId, listingId, status, pageable);
        } else if (listingId != null) {
            bookings = bookingRepository.findByHostIdAndListingIdOrderByCreatedAtDesc(hostId, listingId, pageable);
        } else if (status != null) {
            bookings = bookingRepository.findByHostIdAndStatusOrderByCreatedAtDesc(hostId, status, pageable);
        } else {
            bookings = bookingRepository.findByHostIdOrderByCreatedAtDesc(hostId, pageable);
        }
        return bookingMapper.toSummaryPage(bookings);
    }

    @Transactional(readOnly = true)
    public BookingResponse getHostBooking(UUID bookingId) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(String.valueOf(bookingId)));
        requireHostOwnership(booking, hostId);
        return bookingMapper.toResponse(booking);
    }

    @Transactional
    public BookingResponse approveBooking(UUID bookingId, String idempotencyKey) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);

        String requestHash = idempotencyService.computeHash(new HostDecisionHashInput(bookingId));
        IdempotencyResolution resolution = idempotencyService.resolve(
                hostId,
                IdempotencyScope.HOST_APPROVE_BOOKING,
                idempotencyKey,
                requestHash);
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeResponse(replay.responseBodyJson());
        }

        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                    .orElseThrow(() -> new BookingNotFoundException(String.valueOf(bookingId)));
            requireHostOwnership(booking, hostId);
            validatePendingHostApproval(booking);

            BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(bookingId)
                    .orElseThrow(() -> new BusinessRuleException("PAYMENT_NOT_FOUND", "Payment not found for booking"));
            validateHostApprovalPayment(payment);

            List<AvailabilityCalendar> availabilityRows = availabilityReserver.lockForBooking(booking);
            validateHeldAvailabilityRows(availabilityRows, booking);

            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setHoldToken(null);
            booking.setHoldExpiresAt(null);
            booking.setHostApprovalExpiresAt(null);
            bookingRepository.save(booking);

            availabilityRows.forEach(row -> {
                row.setStatus(AvailabilityStatus.BOOKED);
                row.setHoldToken(null);
                row.setHoldExpiresAt(null);
            });
            availabilityReserver.saveRows(availabilityRows);

            BookingResponse response = bookingMapper.toResponse(booking);
            idempotencyService.complete(idempotencyKeyId, 200, serializeResponse(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    public BookingResponse rejectBooking(UUID bookingId, String idempotencyKey, String reason) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);

        String sanitizedReason = sanitizeRejectReason(reason);
        String requestHash = idempotencyService.computeHash(new HostRejectDecisionHashInput(bookingId, sanitizedReason));
        IdempotencyResolution resolution = idempotencyService.resolve(
                hostId,
                IdempotencyScope.HOST_REJECT_BOOKING,
                idempotencyKey,
                requestHash);
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeResponse(replay.responseBodyJson());
        }

        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            // Phase 1 — Prepare: validate state and create PENDING void transaction (inside TX)
            PreparedRejectContext prepared = required(transactionTemplate.execute(status ->
                    prepareReject(hostId, bookingId, idempotencyKey, idempotencyKeyId, sanitizedReason)));

            // Phase 2 — Call provider (outside DB TX)
            VoidResult voidResult = callHostRejectVoid(prepared);

            // Phase 3 — Finalize: apply void result and update booking state (inside TX)
            BookingResponse response = required(transactionTemplate.execute(status ->
                    finalizeHostReject(hostId, prepared, voidResult, idempotencyKeyId)));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    private PreparedRejectContext prepareReject(
            UUID hostId, UUID bookingId, String clientIdempotencyKey, UUID idempotencyKeyId, String rejectionReason) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(String.valueOf(bookingId)));
        requireHostOwnership(booking, hostId);
        validatePendingHostApproval(booking);

        // Lock payment second (canonical order)
        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new BusinessRuleException("PAYMENT_NOT_FOUND", "Payment not found for booking"));
        validateHostRejectPayment(payment);

        // Lock availability last (canonical order)
        List<AvailabilityCalendar> availabilityRows = availabilityReserver.lockForBooking(booking);
        validateHeldAvailabilityRows(availabilityRows, booking);

        // Create PENDING void transaction
        String correlationId = correlationIdHelper.getOrGenerate();
        String requestId = UUID.randomUUID().toString();
        PaymentTransaction tx = new PaymentTransaction();
        tx.setBookingPaymentId(payment.getId());
        tx.setBookingId(booking.getId());
        tx.setType(PaymentTransactionType.VOID);
        tx.setStatus(PaymentTransactionStatus.PENDING);
        tx.setAmount(BigDecimal.ZERO);
        tx.setCurrency(payment.getCurrency());
        tx.setProvider(payment.getProvider());
        tx.setProviderRequestId(requestId);
        tx.setProviderRef(payment.getProviderHoldId());
        tx.setIdempotencyKeyId(idempotencyKeyId);
        tx = paymentTransactionRepository.save(tx);

        VoidCommand command = new VoidCommand(
                "rentflow:host-reject:void:" + payment.getId() + ":" + clientIdempotencyKey,
                payment.getProviderHoldId(),
                correlationId,
                requestId,
                payment.getId().toString(),
                correlationId);

        return new PreparedRejectContext(
                booking.getId(), tx.getId(), command,
                requestId, correlationId, idempotencyKeyId, rejectionReason);
    }

    private VoidResult callHostRejectVoid(PreparedRejectContext prepared) {
        try {
            return paymentProviderRouter.route(PaymentProviderType.COREBANK)
                    .voidAuthorization(prepared.command());
        } catch (PaymentProviderUnavailableException e) {
            transactionTemplate.executeWithoutResult(status ->
                    markVoidTransactionFailed(prepared, "PAYMENT_PROVIDER_UNAVAILABLE", e.getMessage()));
            throw e;
        } catch (RuntimeException e) {
            transactionTemplate.executeWithoutResult(status ->
                    markVoidTransactionFailed(prepared, "PAYMENT_PROVIDER_ERROR", e.getMessage()));
            throw e;
        }
    }

    private BookingResponse finalizeHostReject(
            UUID hostId,
            PreparedRejectContext prepared,
            VoidResult voidResult,
            UUID idempotencyKeyId) {
        // Re-lock in canonical order: booking → payment → availability
        Booking booking = bookingRepository.findByIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(String.valueOf(prepared.bookingId())));
        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(booking.getId())
                .orElseThrow(() -> new BusinessRuleException("PAYMENT_NOT_FOUND", "Payment not found for booking"));
        List<AvailabilityCalendar> availabilityRows = availabilityReserver.lockForBooking(booking);
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(prepared.bookingId())));

        validateRejectFinalizationState(hostId, prepared, booking, payment, availabilityRows, tx);

        // Mark payment as voided
        payment.setStatus(PaymentStatus.VOIDED);
        payment.setProviderStatus(voidResult.providerStatus());
        payment.setProviderMetadata(voidResult.providerMetadataJson());
        payment.setVoidRetryRequired(false);
        payment.setVoidRetryNextAt(null);
        payment.setVoidRetryLastError(null);
        bookingPaymentRepository.save(payment);

        // Mark transaction succeeded
        tx.setStatus(PaymentTransactionStatus.SUCCEEDED);
        tx.setProviderResponse(voidResult.providerMetadataJson());
        paymentTransactionRepository.save(tx);

        // Reject booking and free availability
        booking.setStatus(BookingStatus.REJECTED);
        booking.setRejectionReason(prepared.rejectionReason());
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

        BookingResponse response = bookingMapper.toResponse(booking);
        idempotencyService.complete(idempotencyKeyId, 200, serializeResponse(response));
        return response;
    }

    private void markVoidTransactionFailed(
            PreparedRejectContext prepared, String errorCode, String errorMessage) {
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(prepared.bookingId())));
        tx.setStatus(PaymentTransactionStatus.FAILED);
        tx.setProviderErrorCode(errorCode);
        tx.setProviderErrorMessage(errorMessage);
        paymentTransactionRepository.save(tx);
    }

    private void markVoidTransactionUnsafe(
            PaymentTransaction tx,
            String errorMessage) {
        tx.setStatus(PaymentTransactionStatus.FAILED);
        tx.setProviderErrorCode("PAYMENT_FINALIZATION_UNSAFE");
        tx.setProviderErrorMessage(errorMessage);
        paymentTransactionRepository.save(tx);
    }

    private VoidResult voidAuthorization(
            BookingPayment payment,
            String clientIdempotencyKey,
            UUID idempotencyKeyId,
            String keyPrefix) {
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
                    "rentflow:" + keyPrefix + ":void:" + payment.getId() + ":" + clientIdempotencyKey,
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

    private void requireHostOwnership(Booking booking, UUID hostId) {
        if (!booking.getHostId().equals(hostId)) {
            throw new BookingNotFoundException(String.valueOf(booking.getId()));
        }
    }

    private String sanitizeRejectReason(String reason) {
        if (reason == null) {
            throw new BusinessRuleException("VALIDATION_ERROR", "reason is required");
        }
        String sanitized = reason.replaceAll("<[^>]*>", "").trim();
        if (sanitized.isBlank()) {
            throw new BusinessRuleException("VALIDATION_ERROR", "reason is required");
        }
        if (sanitized.length() > 500) {
            throw new BusinessRuleException("VALIDATION_ERROR", "reason must be at most 500 characters");
        }
        return sanitized;
    }

    private void validatePendingHostApproval(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING_HOST_APPROVAL) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking is not pending host approval");
        }
        Instant hostApprovalExpiresAt = booking.getHostApprovalExpiresAt();
        if (hostApprovalExpiresAt == null || !hostApprovalExpiresAt.isAfter(clock.instant())) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Host approval window has expired");
        }
    }

    private void validateHostApprovalPayment(BookingPayment payment) {
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new BusinessRuleException("PAYMENT_INVALID_STATUS", "Payment must be AUTHORIZED");
        }
    }

    private void validateHostRejectPayment(BookingPayment payment) {
        if (payment.getProvider() != PaymentProviderType.COREBANK) {
            throw new BusinessRuleException("PAYMENT_PROVIDER_UNSUPPORTED", "Host reject supports CoreBank payment only");
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

    private void validateRejectFinalizationState(
            UUID hostId,
            PreparedRejectContext prepared,
            Booking booking,
            BookingPayment payment,
            List<AvailabilityCalendar> availabilityRows,
            PaymentTransaction tx) {
        try {
            requireHostOwnership(booking, hostId);
            validatePendingHostApproval(booking);
            validateHostRejectPayment(payment);
            validateHeldAvailabilityRows(availabilityRows, booking);
        } catch (BusinessRuleException | BookingNotFoundException e) {
            markVoidTransactionUnsafe(
                    tx,
                    "CoreBank void succeeded but RentFlow reject finalization was no longer safe: " + e.getMessage());
            throw new BusinessRuleException(
                    "PAYMENT_FINALIZATION_UNSAFE",
                    "Payment provider void succeeded but booking rejection could not be finalized safely");
        }
    }

    private String serializeResponse(BookingResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize booking response", e);
        }
    }

    private BookingResponse deserializeResponse(String responseBodyJson) {
        try {
            return objectMapper.readValue(responseBodyJson, BookingResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize booking response", e);
        }
    }

    private record HostDecisionHashInput(UUID bookingId) {
    }

    private record HostRejectDecisionHashInput(UUID bookingId, String reason) {
    }

    private record PreparedRejectContext(
            UUID bookingId,
            UUID transactionId,
            VoidCommand command,
            String requestId,
            String correlationId,
            UUID idempotencyKeyId,
            String rejectionReason
    ) {
    }

    private <T> T required(T value) {
        if (value == null) {
            throw new IllegalStateException("Transactional callback returned null unexpectedly");
        }
        return value;
    }
}
