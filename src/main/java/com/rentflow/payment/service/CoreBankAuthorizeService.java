package com.rentflow.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.auth.entity.Role;
import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.PaymentProviderUnavailableException;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.EmailVerificationPolicy;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.payment.dto.AuthorizePaymentRequest;
import com.rentflow.payment.dto.AuthorizePaymentResponse;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.CustomerPaymentAccount;
import com.rentflow.payment.entity.PaymentBank;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.entity.PaymentTransactionStatus;
import com.rentflow.payment.entity.PaymentTransactionType;
import com.rentflow.payment.provider.AuthorizeCommand;
import com.rentflow.payment.provider.AuthorizeResult;
import com.rentflow.payment.provider.PaymentProvider;
import com.rentflow.payment.provider.PaymentProviderRouter;
import com.rentflow.payment.provider.VoidCommand;
import com.rentflow.payment.provider.VoidResult;
import com.rentflow.payment.provider.corebank.CoreBankAuthorizationFailedException;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.CustomerPaymentAccountRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CoreBankAuthorizeService {

    private final BookingRepository bookingRepository;
    private final BookingPaymentRepository bookingPaymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CustomerPaymentAccountRepository customerPaymentAccountRepository;
    private final AvailabilityCalendarRepository availabilityCalendarRepository;
    private final IdempotencyService idempotencyService;
    private final IdempotencyFailureMarker idempotencyFailureMarker;
    private final SecurityContext securityContext;
    private final EmailVerificationPolicy emailVerificationPolicy;
    private final PaymentBookingSnapshotParser bookingSnapshotParser;
    private final AuthorizePaymentResponseFactory authorizePaymentResponseFactory;
    private final PaymentProviderRouter paymentProviderRouter;
    private final CorrelationIdHelper correlationIdHelper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final boolean requireEmailVerification;

    public CoreBankAuthorizeService(
            BookingRepository bookingRepository,
            BookingPaymentRepository bookingPaymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            CustomerPaymentAccountRepository customerPaymentAccountRepository,
            AvailabilityCalendarRepository availabilityCalendarRepository,
            IdempotencyService idempotencyService,
            IdempotencyFailureMarker idempotencyFailureMarker,
            SecurityContext securityContext,
            EmailVerificationPolicy emailVerificationPolicy,
            PaymentBookingSnapshotParser bookingSnapshotParser,
            AuthorizePaymentResponseFactory authorizePaymentResponseFactory,
            PaymentProviderRouter paymentProviderRouter,
            CorrelationIdHelper correlationIdHelper,
            ObjectMapper objectMapper,
            Clock clock,
            PlatformTransactionManager transactionManager,
            @org.springframework.beans.factory.annotation.Value("${rentflow.payment.require-email-verification:false}")
            boolean requireEmailVerification) {
        this.bookingRepository = bookingRepository;
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.customerPaymentAccountRepository = customerPaymentAccountRepository;
        this.availabilityCalendarRepository = availabilityCalendarRepository;
        this.idempotencyService = idempotencyService;
        this.idempotencyFailureMarker = idempotencyFailureMarker;
        this.securityContext = securityContext;
        this.emailVerificationPolicy = emailVerificationPolicy;
        this.bookingSnapshotParser = bookingSnapshotParser;
        this.authorizePaymentResponseFactory = authorizePaymentResponseFactory;
        this.paymentProviderRouter = paymentProviderRouter;
        this.correlationIdHelper = correlationIdHelper;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.requireEmailVerification = requireEmailVerification;
    }

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
            PreparedAuthorizeContext prepared = required(transactionTemplate.execute(status ->
                prepareAuthorize(customerId, bookingId, request, bank, idempotencyKey, idempotencyKeyId)));
            AuthorizeResult authorizeResult;
            try {
                authorizeResult = routedCoreBankProvider().authorize(prepared.command());
            } catch (CoreBankAuthorizationFailedException e) {
                transactionTemplate.executeWithoutResult(status -> recordBusinessFailure(prepared, e));
                throw e;
            } catch (PaymentProviderUnavailableException e) {
                transactionTemplate.executeWithoutResult(status -> recordProviderUnavailable(prepared, e));
                throw e;
            }

            try {
                AuthorizePaymentResponse response = required(transactionTemplate.execute(status ->
                        finalizeSuccess(prepared, authorizeResult)));
                idempotencyService.complete(idempotencyKeyId, 200, serialize(response));
                return response;
            } catch (RuntimeException e) {
                compensateAfterFinalizationFailure(prepared, authorizeResult, idempotencyKey);
                throw new PaymentProviderUnavailableException(
                        "CoreBank authorization succeeded but RentFlow finalization failed", e);
            }
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    private PreparedAuthorizeContext prepareAuthorize(
            UUID customerId,
            UUID bookingId,
            AuthorizePaymentRequest request,
            PaymentBank bank,
            String clientIdempotencyKey,
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

        PaymentBookingSnapshotParser.PriceSnapshot priceSnapshot = bookingSnapshotParser.readPriceSnapshot(booking);
        PaymentBookingSnapshotParser.PolicySnapshot policySnapshot = bookingSnapshotParser.readPolicySnapshot(booking);
        CustomerPaymentAccount customerPaymentAccount = customerPaymentAccountRepository
                .findByUserIdAndProviderAndActiveTrue(customerId, PaymentProviderType.COREBANK)
                .orElseThrow(() -> new BusinessRuleException(
                        "PAYMENT_ACCOUNT_NOT_LINKED",
                        "Customer does not have an active CoreBank payment account"));

        BookingPayment bookingPayment = bookingPaymentRepository.findByBookingIdForUpdate(bookingId)
                .map(existing -> validateMutablePayment(existing, bookingId))
                .orElseGet(BookingPayment::new);

        String correlationId = correlationIdHelper.getOrGenerate();
        String requestId = UUID.randomUUID().toString();
        String sessionId = bookingId.toString();
        String traceId = correlationId;
        String providerIdempotencyKey = providerAuthorizeIdempotencyKey(bookingId, clientIdempotencyKey);
        String externalOrderRef = externalOrderRef(bookingId);

        bookingPayment.setBookingId(bookingId);
        bookingPayment.setSelectedBankId(bank.getId());
        bookingPayment.setPaymentMethod(bank.getPaymentMethod());
        bookingPayment.setProvider(PaymentProviderType.COREBANK);
        bookingPayment.setStatus(PaymentStatus.UNPAID);
        bookingPayment.setAuthorizedAmount(BigDecimal.ZERO);
        bookingPayment.setCapturedAmount(BigDecimal.ZERO);
        bookingPayment.setRefundedAmount(BigDecimal.ZERO);
        bookingPayment.setCurrency(priceSnapshot.currency());
        bookingPayment.setExternalOrderRef(externalOrderRef);
        bookingPayment.setProviderPaymentOrderId(null);
        bookingPayment.setProviderHoldId(null);
        bookingPayment.setProviderStatus("AUTHORIZE_PENDING");
        bookingPayment.setProviderMetadata(null);
        BookingPayment savedPayment = bookingPaymentRepository.save(bookingPayment);

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setBookingPaymentId(savedPayment.getId());
        transaction.setBookingId(bookingId);
        transaction.setType(PaymentTransactionType.AUTHORIZE);
        transaction.setStatus(PaymentTransactionStatus.PENDING);
        transaction.setAmount(priceSnapshot.totalAmount());
        transaction.setCurrency(priceSnapshot.currency());
        transaction.setProvider(PaymentProviderType.COREBANK);
        transaction.setProviderRequestId(requestId);
        transaction.setIdempotencyKeyId(idempotencyKeyId);
        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

        return new PreparedAuthorizeContext(
                bookingId,
                customerId,
                bank,
                savedPayment.getId(),
                savedTransaction.getId(),
                priceSnapshot,
                policySnapshot,
                new AuthorizeCommand(
                        booking,
                        bank,
                        request.paymentMethod(),
                        priceSnapshot.totalAmount(),
                        priceSnapshot.currency(),
                        externalOrderRef,
                        providerIdempotencyKey,
                        customerPaymentAccount.getProviderAccountId(),
                        correlationId,
                        requestId,
                        sessionId,
                        traceId),
                requestId,
                correlationId,
                sessionId,
                traceId);
    }

    private AuthorizePaymentResponse finalizeSuccess(
            PreparedAuthorizeContext prepared,
            AuthorizeResult authorizeResult) {
        Booking booking = bookingRepository.findByIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(String.valueOf(prepared.bookingId())));
        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new IllegalStateException("Booking payment not found during CoreBank finalize"));
        PaymentTransaction transaction = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new IllegalStateException("Payment transaction not found during CoreBank finalize"));

        List<AvailabilityCalendar> availabilityRows = availabilityCalendarRepository.findForBookingRangeForUpdate(
                booking.getListingId(), booking.getPickupDate(), booking.getReturnDate());
        validateAvailabilityRows(availabilityRows, booking);

        payment.setSelectedBankId(prepared.bank().getId());
        payment.setPaymentMethod(prepared.bank().getPaymentMethod());
        payment.setProvider(PaymentProviderType.COREBANK);
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthorizedAmount(prepared.priceSnapshot().totalAmount());
        payment.setCapturedAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setCurrency(prepared.priceSnapshot().currency());
        payment.setExternalOrderRef(prepared.command().externalOrderRef());
        payment.setProviderPaymentOrderId(authorizeResult.providerPaymentOrderId());
        payment.setProviderHoldId(authorizeResult.providerHoldId());
        payment.setProviderStatus(authorizeResult.providerStatus());
        payment.setProviderMetadata(authorizeResult.providerMetadataJson());

        transaction.setStatus(PaymentTransactionStatus.SUCCEEDED);
        transaction.setAmount(prepared.priceSnapshot().totalAmount());
        transaction.setCurrency(prepared.priceSnapshot().currency());
        transaction.setProviderRequestId(prepared.requestId());
        transaction.setProviderRef(authorizeResult.providerPaymentOrderId());
        transaction.setProviderResponse(authorizeResult.providerMetadataJson());
        transaction.setProviderErrorCode(null);
        transaction.setProviderErrorMessage(null);

        if (prepared.policySnapshot().instantBook()) {
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
            Instant hostApprovalExpiresAt = Instant.now(clock).plus(24, ChronoUnit.HOURS);
            booking.setStatus(BookingStatus.PENDING_HOST_APPROVAL);
            booking.setHoldExpiresAt(null);
            booking.setHostApprovalExpiresAt(hostApprovalExpiresAt);
            availabilityRows.forEach(row -> {
                row.setStatus(AvailabilityStatus.HOLD);
                row.setHoldExpiresAt(hostApprovalExpiresAt);
            });
        }

        availabilityCalendarRepository.saveAll(availabilityRows);
        bookingRepository.save(booking);
        bookingPaymentRepository.save(payment);
        paymentTransactionRepository.save(transaction);
        return authorizePaymentResponseFactory.create(
                booking,
                payment,
                prepared.priceSnapshot().totalAmount(),
                prepared.priceSnapshot().currency(),
                null);
    }

    private void recordBusinessFailure(
            PreparedAuthorizeContext prepared,
            CoreBankAuthorizationFailedException exception) {
        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new IllegalStateException("Booking payment not found during failure handling"));
        PaymentTransaction transaction = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new IllegalStateException("Payment transaction not found during failure handling"));

        payment.setSelectedBankId(prepared.bank().getId());
        payment.setPaymentMethod(prepared.bank().getPaymentMethod());
        payment.setProvider(PaymentProviderType.COREBANK);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setAuthorizedAmount(BigDecimal.ZERO);
        payment.setCapturedAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setCurrency(prepared.priceSnapshot().currency());
        payment.setExternalOrderRef(prepared.command().externalOrderRef());
        payment.setProviderPaymentOrderId(null);
        payment.setProviderHoldId(null);
        payment.setProviderStatus(exception.getProviderStatus());
        payment.setProviderMetadata(exception.getRawResponseJson());

        transaction.setStatus(PaymentTransactionStatus.FAILED);
        transaction.setProviderRequestId(prepared.requestId());
        transaction.setProviderResponse(exception.getRawResponseJson());
        transaction.setProviderErrorCode(exception.getProviderErrorCode());
        transaction.setProviderErrorMessage(exception.getProviderErrorMessage());

        bookingPaymentRepository.save(payment);
        paymentTransactionRepository.save(transaction);
    }

    private void recordProviderUnavailable(
            PreparedAuthorizeContext prepared,
            PaymentProviderUnavailableException exception) {
        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new IllegalStateException("Booking payment not found during provider failure"));
        PaymentTransaction transaction = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new IllegalStateException("Payment transaction not found during provider failure"));

        payment.setSelectedBankId(prepared.bank().getId());
        payment.setPaymentMethod(prepared.bank().getPaymentMethod());
        payment.setProvider(PaymentProviderType.COREBANK);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setAuthorizedAmount(BigDecimal.ZERO);
        payment.setCapturedAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setCurrency(prepared.priceSnapshot().currency());
        payment.setExternalOrderRef(prepared.command().externalOrderRef());
        payment.setProviderPaymentOrderId(null);
        payment.setProviderHoldId(null);
        payment.setProviderStatus("PROVIDER_UNAVAILABLE");
        payment.setProviderMetadata(serialize(Map.of(
                "message", exception.getMessage(),
                "providerStatus", "PROVIDER_UNAVAILABLE")));

        transaction.setStatus(PaymentTransactionStatus.FAILED);
        transaction.setProviderRequestId(prepared.requestId());
        transaction.setProviderErrorCode("PAYMENT_PROVIDER_UNAVAILABLE");
        transaction.setProviderErrorMessage(exception.getMessage());
        transaction.setProviderResponse(payment.getProviderMetadata());

        bookingPaymentRepository.save(payment);
        paymentTransactionRepository.save(transaction);
    }

    private void compensateAfterFinalizationFailure(
            PreparedAuthorizeContext prepared,
            AuthorizeResult authorizeResult,
            String clientIdempotencyKey) {
        transactionTemplate.executeWithoutResult(status ->
                markCompensationRequired(prepared, authorizeResult));

        try {
            VoidResult voidResult = routedCoreBankProvider().voidAuthorization(new VoidCommand(
                    providerVoidIdempotencyKey(prepared.paymentId(), clientIdempotencyKey),
                    authorizeResult.providerHoldId(),
                    prepared.correlationId(),
                    UUID.randomUUID().toString(),
                    prepared.paymentId().toString(),
                    prepared.traceId()));
            transactionTemplate.executeWithoutResult(status ->
                    markCompensatedFailure(prepared, authorizeResult, voidResult));
        } catch (RuntimeException compensationFailure) {
            transactionTemplate.executeWithoutResult(status ->
                    markReconciliationRequired(prepared, authorizeResult, compensationFailure));
        }
    }

    private void markCompensationRequired(
            PreparedAuthorizeContext prepared,
            AuthorizeResult authorizeResult) {
        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new IllegalStateException("Booking payment not found during compensation handling"));
        PaymentTransaction transaction = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new IllegalStateException("Payment transaction not found during compensation handling"));

        payment.setSelectedBankId(prepared.bank().getId());
        payment.setPaymentMethod(prepared.bank().getPaymentMethod());
        payment.setProvider(PaymentProviderType.COREBANK);
        payment.setCurrency(prepared.priceSnapshot().currency());
        payment.setExternalOrderRef(prepared.command().externalOrderRef());
        payment.setProviderPaymentOrderId(authorizeResult.providerPaymentOrderId());
        payment.setProviderHoldId(authorizeResult.providerHoldId());
        payment.setProviderStatus(authorizeResult.providerStatus());
        payment.setProviderMetadata(authorizeResult.providerMetadataJson());

        transaction.setStatus(PaymentTransactionStatus.COMPENSATION_REQUIRED);
        transaction.setProviderRequestId(prepared.requestId());
        transaction.setProviderRef(authorizeResult.providerPaymentOrderId());
        transaction.setProviderResponse(authorizeResult.providerMetadataJson());

        bookingPaymentRepository.save(payment);
        paymentTransactionRepository.save(transaction);
    }

    private void markCompensatedFailure(
            PreparedAuthorizeContext prepared,
            AuthorizeResult authorizeResult,
            VoidResult voidResult) {
        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new IllegalStateException("Booking payment not found during compensated failure"));

        payment.setStatus(PaymentStatus.FAILED);
        payment.setAuthorizedAmount(BigDecimal.ZERO);
        payment.setCapturedAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setProviderPaymentOrderId(authorizeResult.providerPaymentOrderId());
        payment.setProviderHoldId(authorizeResult.providerHoldId());
        payment.setProviderStatus(voidResult.providerStatus());
        payment.setProviderMetadata(voidResult.providerMetadataJson());
        bookingPaymentRepository.save(payment);
    }

    private void markReconciliationRequired(
            PreparedAuthorizeContext prepared,
            AuthorizeResult authorizeResult,
            RuntimeException compensationFailure) {
        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new IllegalStateException("Booking payment not found during reconciliation handling"));
        PaymentTransaction transaction = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new IllegalStateException("Payment transaction not found during reconciliation handling"));

        payment.setStatus(PaymentStatus.RECONCILIATION_REQUIRED);
        payment.setAuthorizedAmount(prepared.priceSnapshot().totalAmount());
        payment.setCapturedAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setProviderPaymentOrderId(authorizeResult.providerPaymentOrderId());
        payment.setProviderHoldId(authorizeResult.providerHoldId());
        payment.setProviderStatus(authorizeResult.providerStatus());
        payment.setProviderMetadata(authorizeResult.providerMetadataJson());

        transaction.setStatus(PaymentTransactionStatus.COMPENSATION_REQUIRED);
        transaction.setProviderErrorCode("COMPENSATION_FAILED");
        transaction.setProviderErrorMessage(compensationFailure.getMessage());

        bookingPaymentRepository.save(payment);
        paymentTransactionRepository.save(transaction);
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

    private void validateAvailabilityRows(List<AvailabilityCalendar> availabilityRows, Booking booking) {
        long expectedDays = ChronoUnit.DAYS.between(booking.getPickupDate(), booking.getReturnDate());
        boolean invalid = availabilityRows.size() != expectedDays
                || availabilityRows.stream().anyMatch(row -> row.getStatus() != AvailabilityStatus.HOLD)
                || availabilityRows.stream().anyMatch(row -> !booking.getId().equals(row.getBookingId()));
        if (invalid) {
            throw new BusinessRuleException(
                    "BOOKING_INVALID_STATUS",
                    "Availability is no longer reserved for this booking");
        }
    }

    private String externalOrderRef(UUID bookingId) {
        return "rentflow:booking:" + bookingId;
    }

    private String providerAuthorizeIdempotencyKey(UUID bookingId, String clientIdempotencyKey) {
        return "rentflow:authorize:" + bookingId + ":" + clientIdempotencyKey;
    }

    private String providerVoidIdempotencyKey(UUID paymentId, String clientIdempotencyKey) {
        return "rentflow:void:" + paymentId + ":" + clientIdempotencyKey;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize CoreBank payload", e);
        }
    }

    private AuthorizePaymentResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, AuthorizePaymentResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize authorize payment response", e);
        }
    }

    private <T> T required(T value) {
        if (value == null) {
            throw new IllegalStateException("Transactional callback returned null unexpectedly");
        }
        return value;
    }

    private PaymentProvider routedCoreBankProvider() {
        return paymentProviderRouter.route(PaymentProviderType.COREBANK);
    }

    private void requireVerifiedEmailForPayment(UUID customerId) {
        if (!requireEmailVerification) {
            return;
        }
        emailVerificationPolicy.requireVerifiedEmail(customerId);
    }

    private record AuthorizeHashInput(
            UUID bookingId,
            AuthorizePaymentRequest request
    ) {
    }

    private record PreparedAuthorizeContext(
            UUID bookingId,
            UUID customerId,
            PaymentBank bank,
            UUID paymentId,
            UUID transactionId,
            PaymentBookingSnapshotParser.PriceSnapshot priceSnapshot,
            PaymentBookingSnapshotParser.PolicySnapshot policySnapshot,
            AuthorizeCommand command,
            String requestId,
            String correlationId,
            String sessionId,
            String traceId
    ) {
    }
}
