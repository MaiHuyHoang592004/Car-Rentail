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
import org.springframework.transaction.annotation.Transactional;

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
            Clock clock) {
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
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingSummaryResponse> listHostBookings(BookingStatus status, Pageable pageable) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);

        Page<Booking> bookings = status == null
                ? bookingRepository.findByHostIdOrderByCreatedAtDesc(hostId, pageable)
                : bookingRepository.findByHostIdAndStatusOrderByCreatedAtDesc(hostId, status, pageable);
        return bookingMapper.toSummaryPage(bookings);
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

    @Transactional
    public BookingResponse rejectBooking(UUID bookingId, String idempotencyKey) {
        UUID hostId = securityContext.currentUserId();
        securityContext.requireRole(Role.HOST);

        String requestHash = idempotencyService.computeHash(new HostDecisionHashInput(bookingId));
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
            Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                    .orElseThrow(() -> new BookingNotFoundException(String.valueOf(bookingId)));
            requireHostOwnership(booking, hostId);
            validatePendingHostApproval(booking);

            BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(bookingId)
                    .orElseThrow(() -> new BusinessRuleException("PAYMENT_NOT_FOUND", "Payment not found for booking"));
            validateHostRejectPayment(payment);

            List<AvailabilityCalendar> availabilityRows = availabilityReserver.lockForBooking(booking);
            validateHeldAvailabilityRows(availabilityRows, booking);

            VoidResult voidResult = voidAuthorization(payment, idempotencyKey, idempotencyKeyId, "host-reject");

            payment.setStatus(PaymentStatus.VOIDED);
            payment.setProviderStatus(voidResult.providerStatus());
            payment.setProviderMetadata(voidResult.providerMetadataJson());
            payment.setVoidRetryRequired(false);
            payment.setVoidRetryNextAt(null);
            payment.setVoidRetryLastError(null);
            bookingPaymentRepository.save(payment);

            booking.setStatus(BookingStatus.REJECTED);
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
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
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
}
