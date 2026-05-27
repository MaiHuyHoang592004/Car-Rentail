package com.rentflow.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.auth.entity.Role;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.PaymentNotFoundException;
import com.rentflow.common.exception.PaymentProviderUnavailableException;
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
import com.rentflow.payment.provider.PaymentProviderRouter;
import com.rentflow.payment.provider.VoidCommand;
import com.rentflow.payment.provider.VoidResult;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class CoreBankVoidService {

    private final BookingRepository bookingRepository;
    private final BookingPaymentRepository bookingPaymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentDetailResponseFactory paymentDetailResponseFactory;
    private final SecurityContext securityContext;
    private final IdempotencyService idempotencyService;
    private final IdempotencyFailureMarker idempotencyFailureMarker;
    private final PaymentProviderRouter paymentProviderRouter;
    private final CorrelationIdHelper correlationIdHelper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public CoreBankVoidService(
            BookingRepository bookingRepository,
            BookingPaymentRepository bookingPaymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            PaymentDetailResponseFactory paymentDetailResponseFactory,
            SecurityContext securityContext,
            IdempotencyService idempotencyService,
            IdempotencyFailureMarker idempotencyFailureMarker,
            PaymentProviderRouter paymentProviderRouter,
            CorrelationIdHelper correlationIdHelper,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentDetailResponseFactory = paymentDetailResponseFactory;
        this.securityContext = securityContext;
        this.idempotencyService = idempotencyService;
        this.idempotencyFailureMarker = idempotencyFailureMarker;
        this.paymentProviderRouter = paymentProviderRouter;
        this.correlationIdHelper = correlationIdHelper;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PaymentDetailResponse voidAuthorization(UUID paymentId, String idempotencyKey) {
        UUID actorId = securityContext.currentUserId();
        String requestHash = idempotencyService.computeHash(new VoidHashInput(paymentId));
        IdempotencyResolution resolution = idempotencyService.resolve(
                actorId,
                IdempotencyScope.VOID_PAYMENT,
                idempotencyKey,
                requestHash);
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeResponse(replay.responseBodyJson());
        }
        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            PreparedVoidContext prepared = required(transactionTemplate.execute(status ->
                    prepareVoid(actorId, paymentId, idempotencyKeyId, idempotencyKey)));
            VoidResult voidResult;
            try {
                voidResult = paymentProviderRouter.route(PaymentProviderType.COREBANK)
                        .voidAuthorization(prepared.command());
            } catch (PaymentProviderUnavailableException e) {
                transactionTemplate.executeWithoutResult(status -> markFailed(prepared, "PAYMENT_PROVIDER_UNAVAILABLE", e.getMessage()));
                throw e;
            }
            PaymentDetailResponse response = required(transactionTemplate.execute(status ->
                    finalizeVoid(prepared, voidResult)));
            idempotencyService.complete(idempotencyKeyId, 200, serializeResponse(response));
            return response;
        } catch (PaymentProviderUnavailableException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        } catch (RuntimeException e) {
            idempotencyFailureMarker.markFailed(idempotencyKeyId);
            throw e;
        }
    }

    private PreparedVoidContext prepareVoid(
            UUID actorId,
            UUID paymentId,
            UUID idempotencyKeyId,
            String clientIdempotencyKey) {
        BookingPayment payment = bookingPaymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(paymentId)));
        Booking booking = bookingRepository.findByIdForUpdate(payment.getBookingId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(paymentId)));
        requireCanMutate(actorId, booking);
        validateVoid(payment);

        String correlationId = correlationIdHelper.getOrGenerate();
        String requestId = UUID.randomUUID().toString();
        PaymentTransaction tx = new PaymentTransaction();
        tx.setBookingPaymentId(payment.getId());
        tx.setBookingId(booking.getId());
        tx.setType(PaymentTransactionType.VOID);
        tx.setStatus(PaymentTransactionStatus.PENDING);
        tx.setAmount(java.math.BigDecimal.ZERO);
        tx.setCurrency(payment.getCurrency());
        tx.setProvider(payment.getProvider());
        tx.setProviderRequestId(requestId);
        tx.setProviderRef(payment.getProviderHoldId());
        tx.setIdempotencyKeyId(idempotencyKeyId);
        tx = paymentTransactionRepository.save(tx);

        VoidCommand command = new VoidCommand(
                "rentflow:void:" + payment.getId() + ":" + clientIdempotencyKey,
                payment.getProviderHoldId(),
                correlationId,
                requestId,
                payment.getId().toString(),
                correlationId);
        return new PreparedVoidContext(payment.getId(), booking.getId(), tx.getId(), command);
    }

    private PaymentDetailResponse finalizeVoid(PreparedVoidContext prepared, VoidResult result) {
        BookingPayment payment = bookingPaymentRepository.findByIdForUpdate(prepared.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(prepared.paymentId())));
        Booking booking = bookingRepository.findByIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(prepared.paymentId())));
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(prepared.paymentId())));

        payment.setStatus(PaymentStatus.VOIDED);
        payment.setProviderStatus(result.providerStatus());
        payment.setProviderMetadata(result.providerMetadataJson());

        tx.setStatus(PaymentTransactionStatus.SUCCEEDED);
        tx.setProviderResponse(result.providerMetadataJson());

        bookingPaymentRepository.save(payment);
        paymentTransactionRepository.save(tx);
        return paymentDetailResponseFactory.create(
                booking,
                payment,
                paymentTransactionRepository.findByBookingPaymentIdOrderByCreatedAtAsc(payment.getId()));
    }

    private void markFailed(PreparedVoidContext prepared, String providerErrorCode, String providerErrorMessage) {
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(prepared.paymentId())));
        tx.setStatus(PaymentTransactionStatus.FAILED);
        tx.setProviderErrorCode(providerErrorCode);
        tx.setProviderErrorMessage(providerErrorMessage);
        paymentTransactionRepository.save(tx);
    }

    private void requireCanMutate(UUID actorId, Booking booking) {
        if (securityContext.hasRole(Role.ADMIN)) {
            return;
        }
        if (securityContext.hasRole(Role.HOST) && booking.getHostId().equals(actorId)) {
            return;
        }
        throw new PaymentNotFoundException(String.valueOf(booking.getId()));
    }

    private void validateVoid(BookingPayment payment) {
        if (payment.getProvider() != PaymentProviderType.COREBANK) {
            throw new BusinessRuleException("PAYMENT_PROVIDER_UNSUPPORTED", "Void is supported only for CoreBank");
        }
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new BusinessRuleException("PAYMENT_INVALID_STATUS", "Payment is not in AUTHORIZED status");
        }
        if (payment.getCapturedAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            throw new BusinessRuleException("PAYMENT_VOID_CAPTURED_NOT_ALLOWED", "Cannot void a payment with captured amount");
        }
        if (payment.getProviderHoldId() == null || payment.getProviderHoldId().isBlank()) {
            throw new BusinessRuleException("PAYMENT_PROVIDER_REFERENCE_MISSING", "Payment is missing provider hold reference");
        }
    }

    private String serializeResponse(PaymentDetailResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payment detail response", e);
        }
    }

    private PaymentDetailResponse deserializeResponse(String responseBodyJson) {
        try {
            return objectMapper.readValue(responseBodyJson, PaymentDetailResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize payment detail response", e);
        }
    }

    private <T> T required(T value) {
        if (value == null) {
            throw new IllegalStateException("Transactional callback returned null unexpectedly");
        }
        return value;
    }

    private record PreparedVoidContext(
            UUID paymentId,
            UUID bookingId,
            UUID transactionId,
            VoidCommand command
    ) {
    }

    private record VoidHashInput(
            UUID paymentId
    ) {
    }
}
