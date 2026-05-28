package com.rentflow.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.auth.entity.Role;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.EmailVerificationPolicy;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.payment.dto.AuthorizePaymentRequest;
import com.rentflow.payment.dto.AuthorizePaymentResponse;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentBank;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.entity.PaymentTransactionStatus;
import com.rentflow.payment.entity.PaymentTransactionType;
import com.rentflow.payment.provider.AuthorizeCommand;
import com.rentflow.payment.provider.AuthorizeResult;
import com.rentflow.payment.provider.PaymentProvider;
import com.rentflow.payment.provider.PaymentProviderRouter;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BankTransferAuthorizeService {

    private final BookingRepository bookingRepository;
    private final BookingPaymentRepository bookingPaymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final IdempotencyService idempotencyService;
    private final IdempotencyFailureMarker idempotencyFailureMarker;
    private final PaymentProviderRouter paymentProviderRouter;
    private final SecurityContext securityContext;
    private final EmailVerificationPolicy emailVerificationPolicy;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PaymentBookingSnapshotParser bookingSnapshotParser;
    private final AuthorizePaymentResponseFactory authorizePaymentResponseFactory;
    private final boolean requireEmailVerification;

    public BankTransferAuthorizeService(
            BookingRepository bookingRepository,
            BookingPaymentRepository bookingPaymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            IdempotencyService idempotencyService,
            IdempotencyFailureMarker idempotencyFailureMarker,
            PaymentProviderRouter paymentProviderRouter,
            SecurityContext securityContext,
            EmailVerificationPolicy emailVerificationPolicy,
            ObjectMapper objectMapper,
            Clock clock,
            PaymentBookingSnapshotParser bookingSnapshotParser,
            AuthorizePaymentResponseFactory authorizePaymentResponseFactory,
            @Value("${rentflow.payment.require-email-verification:false}") boolean requireEmailVerification) {
        this.bookingRepository = bookingRepository;
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.idempotencyService = idempotencyService;
        this.idempotencyFailureMarker = idempotencyFailureMarker;
        this.paymentProviderRouter = paymentProviderRouter;
        this.securityContext = securityContext;
        this.emailVerificationPolicy = emailVerificationPolicy;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.bookingSnapshotParser = bookingSnapshotParser;
        this.authorizePaymentResponseFactory = authorizePaymentResponseFactory;
        this.requireEmailVerification = requireEmailVerification;
    }

    @Transactional
    public AuthorizePaymentResponse authorizeBookingPayment(
            UUID bookingId,
            String idempotencyKey,
            AuthorizePaymentRequest request,
            PaymentBank bank) {
        UUID customerId = securityContext.currentUserId();
        securityContext.requireRole(Role.CUSTOMER);

        String requestHash = idempotencyService.computeHash(new AuthorizeHashInput(bookingId, request));
        IdempotencyResolution resolution = idempotencyService.resolve(
                customerId,
                IdempotencyScope.AUTHORIZE_PAYMENT,
                idempotencyKey,
                requestHash);

        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeResponse(replay.responseBodyJson());
        }

        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            requireVerifiedEmailForPayment(customerId);
            AuthorizePaymentResponse response = authorizeAfterIdempotency(customerId, bookingId, request, bank, idempotencyKeyId);
            idempotencyService.complete(idempotencyKeyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    private AuthorizePaymentResponse authorizeAfterIdempotency(
            UUID customerId,
            UUID bookingId,
            AuthorizePaymentRequest request,
            PaymentBank bank,
            UUID idempotencyKeyId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(String.valueOf(bookingId)));
        if (!booking.getCustomerId().equals(customerId)) {
            throw new BookingNotFoundException(String.valueOf(bookingId));
        }
        if (booking.getStatus() != BookingStatus.HELD) {
            throw new BusinessRuleException(
                    "BOOKING_INVALID_STATUS",
                    "Booking cannot be authorized in its current status");
        }
        if (booking.getHoldExpiresAt() == null || !booking.getHoldExpiresAt().isAfter(Instant.now(clock))) {
            throw new BusinessRuleException(
                    "BOOKING_HOLD_EXPIRED",
                    "Booking hold has expired");
        }
        if (bank.getPaymentMethod() != request.paymentMethod()) {
            throw new ValidationException("Selected bank does not support the requested payment method");
        }

        BookingPayment bookingPayment = bookingPaymentRepository.findByBookingIdForUpdate(bookingId)
                .map(existing -> validateMutablePayment(existing, bookingId))
                .orElseGet(BookingPayment::new);

        PaymentBookingSnapshotParser.PriceSnapshot priceSnapshot = bookingSnapshotParser.readPriceSnapshot(booking);
        PaymentProvider provider = paymentProviderRouter.route(bank.getProvider());
        String externalOrderRef = externalOrderRef(bookingId);
        AuthorizeResult authorizeResult = provider.authorize(new AuthorizeCommand(
                booking,
                bank,
                request.paymentMethod(),
                priceSnapshot.totalAmount(),
                priceSnapshot.currency(),
                externalOrderRef,
                null,
                null,
                null,
                null,
                null,
                null));

        applyAuthorizeResult(bookingPayment, booking, bank, priceSnapshot, externalOrderRef, authorizeResult);
        BookingPayment savedPayment = bookingPaymentRepository.save(bookingPayment);
        paymentTransactionRepository.save(buildAuthorizeTransaction(savedPayment, booking, idempotencyKeyId, authorizeResult));

        return authorizePaymentResponseFactory.create(
                booking,
                savedPayment,
                priceSnapshot.totalAmount(),
                priceSnapshot.currency(),
                authorizeResult.transferInstruction());
    }

    private BookingPayment validateMutablePayment(BookingPayment bookingPayment, UUID bookingId) {
        if (bookingPayment.getStatus() != PaymentStatus.UNPAID
                && bookingPayment.getStatus() != PaymentStatus.PENDING_TRANSFER
                && bookingPayment.getStatus() != PaymentStatus.FAILED) {
            throw new BusinessRuleException(
                    "PAYMENT_INVALID_STATUS",
                    "Payment cannot be authorized in its current status for booking " + bookingId);
        }
        return bookingPayment;
    }

    private void requireVerifiedEmailForPayment(UUID customerId) {
        if (!requireEmailVerification) {
            return;
        }
        emailVerificationPolicy.requireVerifiedEmail(customerId);
    }

    private void applyAuthorizeResult(
            BookingPayment bookingPayment,
            Booking booking,
            PaymentBank bank,
            PaymentBookingSnapshotParser.PriceSnapshot priceSnapshot,
            String externalOrderRef,
            AuthorizeResult authorizeResult) {
        bookingPayment.setBookingId(booking.getId());
        bookingPayment.setSelectedBankId(bank.getId());
        bookingPayment.setPaymentMethod(bank.getPaymentMethod());
        bookingPayment.setProvider(authorizeResult.provider());
        bookingPayment.setStatus(authorizeResult.paymentStatus());
        bookingPayment.setAuthorizedAmount(authorizeResult.authorizedAmount());
        bookingPayment.setCapturedAmount(BigDecimal.ZERO);
        bookingPayment.setRefundedAmount(BigDecimal.ZERO);
        bookingPayment.setCurrency(priceSnapshot.currency());
        bookingPayment.setExternalOrderRef(externalOrderRef);
        bookingPayment.setProviderStatus(authorizeResult.providerStatus());
        bookingPayment.setProviderPaymentOrderId(authorizeResult.providerPaymentOrderId());
        bookingPayment.setProviderHoldId(authorizeResult.providerHoldId());
        bookingPayment.setProviderMetadata(resolveProviderMetadata(authorizeResult));
    }

    private PaymentTransaction buildAuthorizeTransaction(
            BookingPayment bookingPayment,
            Booking booking,
            UUID idempotencyKeyId,
            AuthorizeResult authorizeResult) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setBookingPaymentId(bookingPayment.getId());
        transaction.setBookingId(booking.getId());
        transaction.setType(PaymentTransactionType.AUTHORIZE);
        transaction.setStatus(PaymentTransactionStatus.SUCCEEDED);
        transaction.setAmount(authorizeResult.authorizedAmount());
        transaction.setCurrency(bookingPayment.getCurrency());
        transaction.setProvider(authorizeResult.provider());
        transaction.setProviderRef(authorizeResult.providerPaymentOrderId());
        transaction.setProviderResponse(resolveProviderMetadata(authorizeResult));
        transaction.setIdempotencyKeyId(idempotencyKeyId);
        return transaction;
    }

    private String resolveProviderMetadata(AuthorizeResult authorizeResult) {
        if (authorizeResult.providerMetadataJson() != null) {
            return authorizeResult.providerMetadataJson();
        }
        return serialize(providerMetadata(authorizeResult));
    }

    private Map<String, Object> providerMetadata(AuthorizeResult authorizeResult) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("providerStatus", authorizeResult.providerStatus());
        if (authorizeResult.transferInstruction() != null) {
            metadata.put("transferInstruction", authorizeResult.transferInstruction());
        }
        return metadata;
    }

    private String externalOrderRef(UUID bookingId) {
        return "rentflow:booking:" + bookingId;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payment payload", e);
        }
    }

    private AuthorizePaymentResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, AuthorizePaymentResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize authorize payment response", e);
        }
    }

    private record AuthorizeHashInput(
            UUID bookingId,
            AuthorizePaymentRequest request
    ) {
    }
}
