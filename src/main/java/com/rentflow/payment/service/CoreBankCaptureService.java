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
import com.rentflow.payment.dto.CapturePaymentRequest;
import com.rentflow.payment.dto.PaymentDetailResponse;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.entity.PaymentTransactionStatus;
import com.rentflow.payment.entity.PaymentTransactionType;
import com.rentflow.payment.provider.CaptureCommand;
import com.rentflow.payment.provider.CaptureResult;
import com.rentflow.payment.provider.PaymentProviderRouter;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class CoreBankCaptureService {

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
    private final TransactionTemplate requiresNewTransactionTemplate;

    public CoreBankCaptureService(
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
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public PaymentDetailResponse capture(UUID paymentId, String idempotencyKey, CapturePaymentRequest request) {
        UUID actorId = securityContext.currentUserId();
        String requestHash = idempotencyService.computeHash(new CaptureHashInput(paymentId, request.amount()));
        IdempotencyResolution resolution = idempotencyService.resolve(
                actorId,
                IdempotencyScope.CAPTURE_PAYMENT,
                idempotencyKey,
                requestHash);

        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeResponse(replay.responseBodyJson());
        }

        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            PreparedCaptureContext prepared = required(transactionTemplate.execute(status ->
                    prepareCapture(actorId, paymentId, idempotencyKeyId, idempotencyKey, request)));
            CaptureResult captureResult;
            try {
                captureResult = paymentProviderRouter.route(PaymentProviderType.COREBANK)
                        .capture(prepared.command());
            } catch (PaymentProviderUnavailableException e) {
                transactionTemplate.executeWithoutResult(status -> markFailed(prepared, "PAYMENT_PROVIDER_UNAVAILABLE", e.getMessage()));
                throw e;
            }
            PaymentDetailResponse response;
            try {
                response = required(transactionTemplate.execute(status ->
                        finalizeCapture(prepared, captureResult)));
            } catch (BusinessRuleException e) {
                if (isUnsafeFinalization(e)) {
                    markUnsafeRequiresNew(
                            prepared,
                            "CoreBank capture succeeded but local payment finalization was no longer safe");
                }
                throw e;
            }
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

    private PreparedCaptureContext prepareCapture(
            UUID actorId,
            UUID paymentId,
            UUID idempotencyKeyId,
            String clientIdempotencyKey,
            CapturePaymentRequest request) {
        // 1. Read payment to get bookingId (no lock yet — we lock in canonical order)
        BookingPayment payment = bookingPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(paymentId)));
        // 2. Lock booking first (canonical order)
        Booking booking = bookingRepository.findByIdForUpdate(payment.getBookingId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(paymentId)));
        // 3. Lock payment second (canonical order)
        payment = bookingPaymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(paymentId)));
        requireCanMutate(actorId, booking);
        validateCapture(payment, request.amount());

        String correlationId = correlationIdHelper.getOrGenerate();
        String requestId = UUID.randomUUID().toString();
        PaymentTransaction tx = new PaymentTransaction();
        tx.setBookingPaymentId(payment.getId());
        tx.setBookingId(booking.getId());
        tx.setType(PaymentTransactionType.CAPTURE);
        tx.setStatus(PaymentTransactionStatus.PENDING);
        tx.setAmount(request.amount());
        tx.setCurrency(payment.getCurrency());
        tx.setProvider(payment.getProvider());
        tx.setProviderRequestId(requestId);
        tx.setProviderRef(payment.getProviderPaymentOrderId());
        tx.setIdempotencyKeyId(idempotencyKeyId);
        tx = paymentTransactionRepository.save(tx);

        CaptureCommand command = new CaptureCommand(
                "rentflow:capture:" + payment.getId() + ":" + clientIdempotencyKey,
                payment.getProviderPaymentOrderId(),
                request.amount(),
                payment.getCurrency(),
                correlationId,
                requestId,
                payment.getId().toString(),
                correlationId);
        return new PreparedCaptureContext(payment.getId(), booking.getId(), tx.getId(), request.amount(), command);
    }

    private PaymentDetailResponse finalizeCapture(PreparedCaptureContext prepared, CaptureResult result) {
        // Lock booking before payment (canonical order)
        Booking booking = bookingRepository.findByIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(prepared.paymentId())));
        BookingPayment payment = bookingPaymentRepository.findByIdForUpdate(prepared.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(prepared.paymentId())));
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(prepared.paymentId())));

        validateCaptureFinalizationState(prepared, payment);

        BigDecimal updatedCaptured = payment.getCapturedAmount().add(prepared.amount());
        payment.setCapturedAmount(updatedCaptured);
        if (updatedCaptured.compareTo(payment.getAuthorizedAmount()) == 0) {
            payment.setStatus(PaymentStatus.CAPTURED);
        } else {
            payment.setStatus(PaymentStatus.AUTHORIZED);
        }
        payment.setProviderStatus(result.providerStatus());
        payment.setProviderMetadata(result.providerMetadataJson());

        tx.setStatus(PaymentTransactionStatus.SUCCEEDED);
        tx.setProviderJournalId(result.providerJournalId());
        tx.setProviderResponse(result.providerMetadataJson());

        bookingPaymentRepository.save(payment);
        paymentTransactionRepository.save(tx);

        return paymentDetailResponseFactory.create(
                booking,
                payment,
                paymentTransactionRepository.findByBookingPaymentIdOrderByCreatedAtAsc(payment.getId()));
    }

    private void validateCaptureFinalizationState(PreparedCaptureContext prepared, BookingPayment payment) {
        BigDecimal remaining = payment.getAuthorizedAmount().subtract(payment.getCapturedAmount());
        boolean invalid = payment.getProvider() != PaymentProviderType.COREBANK
                || payment.getStatus() != PaymentStatus.AUTHORIZED
                || payment.getProviderPaymentOrderId() == null
                || payment.getProviderPaymentOrderId().isBlank()
                || prepared.amount().compareTo(remaining) > 0;
        if (invalid) {
            throw new BusinessRuleException(
                    "PAYMENT_FINALIZATION_UNSAFE",
                    "Payment capture succeeded but local finalization was no longer safe");
        }
    }

    private void markFailed(PreparedCaptureContext prepared, String providerErrorCode, String providerErrorMessage) {
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(prepared.paymentId())));
        tx.setStatus(PaymentTransactionStatus.FAILED);
        tx.setProviderErrorCode(providerErrorCode);
        tx.setProviderErrorMessage(providerErrorMessage);
        paymentTransactionRepository.save(tx);
    }

    private void markUnsafeRequiresNew(PreparedCaptureContext prepared, String providerErrorMessage) {
        requiresNewTransactionTemplate.executeWithoutResult(status -> {
            PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                    .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(prepared.paymentId())));
            tx.setStatus(PaymentTransactionStatus.FAILED);
            tx.setProviderErrorCode("PAYMENT_FINALIZATION_UNSAFE");
            tx.setProviderErrorMessage(providerErrorMessage);
            paymentTransactionRepository.save(tx);
        });
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

    private void validateCapture(BookingPayment payment, BigDecimal amount) {
        if (payment.getProvider() != PaymentProviderType.COREBANK) {
            throw new BusinessRuleException("PAYMENT_PROVIDER_UNSUPPORTED", "Capture is supported only for CoreBank");
        }
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new BusinessRuleException("PAYMENT_INVALID_STATUS", "Payment is not in AUTHORIZED status");
        }
        if (payment.getProviderPaymentOrderId() == null || payment.getProviderPaymentOrderId().isBlank()) {
            throw new BusinessRuleException("PAYMENT_PROVIDER_REFERENCE_MISSING", "Payment is missing provider payment order reference");
        }
        BigDecimal remaining = payment.getAuthorizedAmount().subtract(payment.getCapturedAmount());
        if (amount.compareTo(remaining) > 0) {
            throw new BusinessRuleException("PAYMENT_CAPTURE_AMOUNT_EXCEEDED", "Capture amount exceeds remaining authorized amount");
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

    private boolean isUnsafeFinalization(BusinessRuleException e) {
        return "PAYMENT_FINALIZATION_UNSAFE".equals(e.getCode());
    }

    private record PreparedCaptureContext(
            UUID paymentId,
            UUID bookingId,
            UUID transactionId,
            BigDecimal amount,
            CaptureCommand command
    ) {
    }

    private record CaptureHashInput(
            UUID paymentId,
            BigDecimal amount
    ) {
    }
}
