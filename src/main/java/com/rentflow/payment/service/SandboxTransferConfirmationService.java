package com.rentflow.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.auth.entity.Role;
import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.booking.service.AvailabilityReserver;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.payment.dto.PaymentDetailResponse;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.entity.PaymentTransactionStatus;
import com.rentflow.payment.entity.PaymentTransactionType;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SandboxTransferConfirmationService {

    public static final String CONFIRMED_STATUS = "SANDBOX_TRANSFER_CONFIRMED";
    public static final String VOIDED_STATUS = "SANDBOX_TRANSFER_VOIDED";

    private final BookingRepository bookingRepository;
    private final BookingPaymentRepository bookingPaymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final AvailabilityReserver availabilityReserver;
    private final SecurityContext securityContext;
    private final PaymentBookingSnapshotParser bookingSnapshotParser;
    private final PaymentDetailResponseFactory paymentDetailResponseFactory;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean enabled;
    private final IdempotencyService idempotencyService;
    private final IdempotencyFailureMarker idempotencyFailureMarker;

    public SandboxTransferConfirmationService(
            BookingRepository bookingRepository,
            BookingPaymentRepository bookingPaymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            AvailabilityReserver availabilityReserver,
            SecurityContext securityContext,
            PaymentBookingSnapshotParser bookingSnapshotParser,
            PaymentDetailResponseFactory paymentDetailResponseFactory,
            ObjectMapper objectMapper,
            Clock clock,
            IdempotencyService idempotencyService,
            IdempotencyFailureMarker idempotencyFailureMarker,
            @Value("${rentflow.payment.sandbox-transfer-confirmation.enabled:false}") boolean enabled) {
        this.bookingRepository = bookingRepository;
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.availabilityReserver = availabilityReserver;
        this.securityContext = securityContext;
        this.bookingSnapshotParser = bookingSnapshotParser;
        this.paymentDetailResponseFactory = paymentDetailResponseFactory;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.idempotencyService = idempotencyService;
        this.idempotencyFailureMarker = idempotencyFailureMarker;
        this.enabled = enabled;
    }

    @Transactional
    public PaymentDetailResponse confirm(UUID bookingId, String idempotencyKey) {
        ensureEnabled();
        UUID actorId = securityContext.currentUserId();
        String requestHash = idempotencyService.computeHash(new ConfirmHashInput(bookingId));
        IdempotencyResolution resolution = idempotencyService.resolve(
                actorId,
                IdempotencyScope.SIMULATE_TRANSFER_CONFIRMATION,
                idempotencyKey,
                requestHash);
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeResponse(replay.responseBodyJson());
        }
        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            PaymentDetailResponse response = doConfirm(actorId, bookingId);
            idempotencyService.complete(idempotencyKeyId, 200, serializeResponse(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    private PaymentDetailResponse doConfirm(UUID actorId, UUID bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        requireCustomerOwnerOrAdmin(booking, actorId);
        validateHeldBooking(booking);

        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new BusinessRuleException("PAYMENT_NOT_FOUND", "Payment not found for booking"));
        validatePendingManualTransfer(payment);

        List<AvailabilityCalendar> availabilityRows = availabilityReserver.lockForBooking(booking);
        validateHeldAvailabilityRows(availabilityRows, booking);

        PaymentBookingSnapshotParser.PriceSnapshot priceSnapshot = bookingSnapshotParser.readPriceSnapshot(booking);
        PaymentBookingSnapshotParser.PolicySnapshot policySnapshot = bookingSnapshotParser.readPolicySnapshot(booking);

        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthorizedAmount(priceSnapshot.totalAmount());
        payment.setCapturedAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setCurrency(priceSnapshot.currency());
        payment.setProviderStatus(CONFIRMED_STATUS);
        payment.setProviderMetadata(mergeSandboxMetadata(payment.getProviderMetadata(), CONFIRMED_STATUS, actorId));
        bookingPaymentRepository.save(payment);

        paymentTransactionRepository.save(buildTransaction(
                payment,
                PaymentTransactionType.AUTHORIZE,
                priceSnapshot.totalAmount(),
                priceSnapshot.currency(),
                CONFIRMED_STATUS,
                actorId));

        if (policySnapshot.instantBook()) {
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setHoldToken(null);
            booking.setHoldExpiresAt(null);
            booking.setHostApprovalExpiresAt(null);
            availabilityRows.forEach(row -> {
                row.setStatus(AvailabilityStatus.BOOKED);
                row.setHoldToken(null);
                row.setHoldExpiresAt(null);
            });
        } else {
            Instant hostApprovalExpiresAt = clock.instant().plus(24, ChronoUnit.HOURS);
            booking.setStatus(BookingStatus.PENDING_HOST_APPROVAL);
            booking.setHoldExpiresAt(null);
            booking.setHostApprovalExpiresAt(hostApprovalExpiresAt);
            availabilityRows.forEach(row -> {
                row.setStatus(AvailabilityStatus.HOLD);
                row.setHoldExpiresAt(hostApprovalExpiresAt);
            });
        }

        bookingRepository.save(booking);
        availabilityReserver.saveRows(availabilityRows);
        return paymentDetailResponseFactory.create(
                booking,
                payment,
                paymentTransactionRepository.findByBookingPaymentIdOrderByCreatedAtAsc(payment.getId()));
    }

    private String serializeResponse(PaymentDetailResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize sandbox payment detail response", e);
        }
    }

    private PaymentDetailResponse deserializeResponse(String responseBodyJson) {
        try {
            return objectMapper.readValue(responseBodyJson, PaymentDetailResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize sandbox payment detail response", e);
        }
    }

    @Transactional
    public PaymentDetailResponse voidSandboxTransfer(UUID bookingId, UUID hostId, String reason) {
        ensureEnabled();
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        if (!booking.getHostId().equals(hostId)) {
            throw new BookingNotFoundException(bookingId.toString());
        }
        validatePendingHostApproval(booking);

        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new BusinessRuleException("PAYMENT_NOT_FOUND", "Payment not found for booking"));
        validateSandboxAuthorizedManualTransfer(payment);

        List<AvailabilityCalendar> availabilityRows = availabilityReserver.lockForBooking(booking);
        validateHeldAvailabilityRows(availabilityRows, booking);

        payment.setStatus(PaymentStatus.VOIDED);
        payment.setProviderStatus(VOIDED_STATUS);
        payment.setProviderMetadata(mergeSandboxMetadata(payment.getProviderMetadata(), VOIDED_STATUS, hostId));
        payment.setVoidRetryRequired(false);
        payment.setVoidRetryNextAt(null);
        payment.setVoidRetryLastError(null);
        bookingPaymentRepository.save(payment);

        paymentTransactionRepository.save(buildTransaction(
                payment,
                PaymentTransactionType.VOID,
                BigDecimal.ZERO,
                payment.getCurrency(),
                VOIDED_STATUS,
                hostId));

        booking.setStatus(BookingStatus.REJECTED);
        booking.setRejectionReason(reason);
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

        return paymentDetailResponseFactory.create(
                booking,
                payment,
                paymentTransactionRepository.findByBookingPaymentIdOrderByCreatedAtAsc(payment.getId()));
    }

    public boolean isSandboxAuthorizedManualTransfer(BookingPayment payment) {
        return enabled
                && payment.getProvider() == PaymentProviderType.VIETQR_MANUAL
                && payment.getStatus() == PaymentStatus.AUTHORIZED
                && CONFIRMED_STATUS.equals(payment.getProviderStatus());
    }

    private void ensureEnabled() {
        if (!enabled) {
            throw new BusinessRuleException(
                    "SANDBOX_PAYMENT_DISABLED",
                    "Sandbox transfer confirmation is disabled");
        }
    }

    private void requireCustomerOwnerOrAdmin(Booking booking, UUID actorId) {
        if (securityContext.hasRole(Role.ADMIN) || booking.getCustomerId().equals(actorId)) {
            return;
        }
        throw new BookingNotFoundException(booking.getId().toString());
    }

    private void validateHeldBooking(Booking booking) {
        if (booking.getStatus() != BookingStatus.HELD) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking is not waiting for payment confirmation");
        }
        if (booking.getHoldExpiresAt() == null || !booking.getHoldExpiresAt().isAfter(clock.instant())) {
            throw new BusinessRuleException("BOOKING_HOLD_EXPIRED", "Booking hold has expired");
        }
    }

    private void validatePendingHostApproval(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING_HOST_APPROVAL) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking is not pending host approval");
        }
        if (booking.getHostApprovalExpiresAt() == null || !booking.getHostApprovalExpiresAt().isAfter(clock.instant())) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Host approval window has expired");
        }
    }

    private void validatePendingManualTransfer(BookingPayment payment) {
        if (payment.getProvider() != PaymentProviderType.VIETQR_MANUAL) {
            throw new BusinessRuleException("PAYMENT_PROVIDER_UNSUPPORTED", "Sandbox confirmation supports manual transfer payments only");
        }
        if (payment.getStatus() != PaymentStatus.PENDING_TRANSFER) {
            throw new BusinessRuleException("PAYMENT_INVALID_STATUS", "Payment must be PENDING_TRANSFER");
        }
    }

    private void validateSandboxAuthorizedManualTransfer(BookingPayment payment) {
        if (!isSandboxAuthorizedManualTransfer(payment)) {
            throw new BusinessRuleException("PAYMENT_INVALID_STATUS", "Payment is not a sandbox-confirmed manual transfer");
        }
    }

    private void validateHeldAvailabilityRows(List<AvailabilityCalendar> rows, Booking booking) {
        long expectedDays = ChronoUnit.DAYS.between(booking.getPickupDate(), booking.getReturnDate());
        boolean invalid = rows.size() != expectedDays
                || rows.stream().anyMatch(row -> row.getStatus() != AvailabilityStatus.HOLD)
                || rows.stream().anyMatch(row -> !booking.getId().equals(row.getBookingId()));
        if (invalid) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Availability is no longer reserved for this booking");
        }
    }

    private PaymentTransaction buildTransaction(
            BookingPayment payment,
            PaymentTransactionType type,
            BigDecimal amount,
            String currency,
            String providerStatus,
            UUID actorId) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setBookingPaymentId(payment.getId());
        tx.setBookingId(payment.getBookingId());
        tx.setType(type);
        tx.setStatus(PaymentTransactionStatus.SUCCEEDED);
        tx.setAmount(amount);
        tx.setCurrency(currency);
        tx.setProvider(PaymentProviderType.VIETQR_MANUAL);
        tx.setProviderRequestId(UUID.randomUUID().toString());
        tx.setProviderResponse(sandboxMetadata(providerStatus, actorId));
        return tx;
    }

    private String mergeSandboxMetadata(String existingMetadata, String providerStatus, UUID actorId) {
        if (existingMetadata == null || existingMetadata.isBlank()) {
            return sandboxMetadata(providerStatus, actorId);
        }
        try {
            Map<?, ?> existing = objectMapper.readValue(existingMetadata, Map.class);
            java.util.LinkedHashMap<String, Object> metadata = new java.util.LinkedHashMap<>();
            existing.forEach((key, value) -> {
                if (key instanceof String stringKey) {
                    metadata.put(stringKey, value);
                }
            });
            metadata.put("sandbox", true);
            metadata.put("providerStatus", providerStatus);
            metadata.put("confirmedBy", actorId.toString());
            metadata.put("confirmedAt", clock.instant().toString());
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return sandboxMetadata(providerStatus, actorId);
        }
    }

    private String sandboxMetadata(String providerStatus, UUID actorId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "sandbox", true,
                    "providerStatus", providerStatus,
                    "confirmedBy", actorId.toString(),
                    "confirmedAt", clock.instant().toString()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize sandbox payment metadata", e);
        }
    }

    private record ConfirmHashInput(UUID bookingId) {
    }
}
