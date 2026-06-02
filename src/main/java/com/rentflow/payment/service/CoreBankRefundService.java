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
import com.rentflow.payment.dto.RefundPaymentRequest;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.entity.PaymentTransactionStatus;
import com.rentflow.payment.entity.PaymentTransactionType;
import com.rentflow.payment.provider.PaymentProviderRouter;
import com.rentflow.payment.provider.RefundCommand;
import com.rentflow.payment.provider.RefundResult;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Service
public class CoreBankRefundService {

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

    public CoreBankRefundService(
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

    public PaymentDetailResponse refund(UUID paymentId, String idempotencyKey, RefundPaymentRequest request) {
        UUID actorId = securityContext.currentUserId();
        String requestHash = idempotencyService.computeHash(new RefundHashInput(paymentId, request.amount(), request.reason()));
        IdempotencyResolution resolution = idempotencyService.resolve(
                actorId,
                IdempotencyScope.REFUND_PAYMENT,
                idempotencyKey,
                requestHash);
        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeResponse(replay.responseBodyJson());
        }
        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            PreparedRefundContext prepared = required(transactionTemplate.execute(status ->
                    prepareRefund(actorId, paymentId, idempotencyKeyId, idempotencyKey, request)));
            RefundResult refundResult;
            try {
                refundResult = paymentProviderRouter.route(PaymentProviderType.COREBANK).refund(prepared.command());
            } catch (PaymentProviderUnavailableException e) {
                transactionTemplate.executeWithoutResult(status ->
                        markFailed(prepared, "PAYMENT_PROVIDER_UNAVAILABLE", e.getMessage()));
                throw e;
            }
            FinalizedPaymentDetail finalized = required(transactionTemplate.execute(status ->
                    finalizeRefund(prepared, refundResult)));
            if (finalized.unsafe()) {
                throw new BusinessRuleException("PAYMENT_FINALIZATION_UNSAFE", finalized.unsafeReason());
            }
            PaymentDetailResponse response = finalized.response();
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

    private PreparedRefundContext prepareRefund(
            UUID actorId,
            UUID paymentId,
            UUID idempotencyKeyId,
            String clientIdempotencyKey,
            RefundPaymentRequest request) {
        // 1. Read payment to get bookingId (no lock yet)
        BookingPayment payment = bookingPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(paymentId)));
        // 2. Lock booking first (canonical order)
        Booking booking = bookingRepository.findByIdForUpdate(payment.getBookingId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(paymentId)));
        // 3. Lock payment second (canonical order)
        payment = bookingPaymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(paymentId)));
        requireCanMutate(actorId, booking);
        validateRefund(payment, request.amount());

        String correlationId = correlationIdHelper.getOrGenerate();
        String requestId = UUID.randomUUID().toString();
        PaymentTransaction tx = new PaymentTransaction();
        tx.setBookingPaymentId(payment.getId());
        tx.setBookingId(booking.getId());
        tx.setType(PaymentTransactionType.REFUND);
        tx.setStatus(PaymentTransactionStatus.PENDING);
        tx.setAmount(request.amount());
        tx.setCurrency(payment.getCurrency());
        tx.setProvider(payment.getProvider());
        tx.setProviderRequestId(requestId);
        tx.setProviderRef(payment.getProviderPaymentOrderId());
        tx.setIdempotencyKeyId(idempotencyKeyId);
        tx = paymentTransactionRepository.save(tx);

        RefundCommand command = new RefundCommand(
                "rentflow:refund:" + payment.getId() + ":" + clientIdempotencyKey,
                payment.getProviderPaymentOrderId(),
                request.amount(),
                payment.getCurrency(),
                request.reason(),
                correlationId,
                requestId,
                payment.getId().toString(),
                correlationId);
        return new PreparedRefundContext(
                payment.getId(),
                booking.getId(),
                tx.getId(),
                request.amount(),
                payment.getProviderPaymentOrderId(),
                command);
    }

    private FinalizedPaymentDetail finalizeRefund(PreparedRefundContext prepared, RefundResult result) {
        // Lock booking before payment (canonical order)
        Booking booking = bookingRepository.findByIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(prepared.paymentId())));
        BookingPayment payment = bookingPaymentRepository.findByIdForUpdate(prepared.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(prepared.paymentId())));
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(prepared.paymentId())));

        String unsafeReason = refundFinalizationUnsafeReason(prepared, payment, tx);
        if (unsafeReason != null) {
            markUnsafe(tx, unsafeReason, result);
            return new FinalizedPaymentDetail(paymentDetailResponseFactory.create(
                    booking,
                    payment,
                    paymentTransactionRepository.findByBookingPaymentIdOrderByCreatedAtAsc(payment.getId())),
                    true,
                    unsafeReason);
        }

        BigDecimal updatedRefunded = payment.getRefundedAmount().add(prepared.amount());
        payment.setRefundedAmount(updatedRefunded);
        if (updatedRefunded.compareTo(payment.getCapturedAmount()) == 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
        } else {
            payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
        payment.setProviderStatus(result.providerStatus());
        payment.setProviderMetadata(result.providerMetadataJson());

        tx.setStatus(PaymentTransactionStatus.SUCCEEDED);
        tx.setProviderJournalId(result.providerJournalId());
        tx.setProviderResponse(result.providerMetadataJson());

        bookingPaymentRepository.save(payment);
        paymentTransactionRepository.save(tx);
        return new FinalizedPaymentDetail(paymentDetailResponseFactory.create(
                booking,
                payment,
                paymentTransactionRepository.findByBookingPaymentIdOrderByCreatedAtAsc(payment.getId())),
                false,
                null);
    }

    private String refundFinalizationUnsafeReason(
            PreparedRefundContext prepared,
            BookingPayment payment,
            PaymentTransaction tx) {
        if (tx.getStatus() != PaymentTransactionStatus.PENDING) {
            return "CoreBank refund succeeded but local transaction is no longer pending";
        }
        if (payment.getProvider() != PaymentProviderType.COREBANK) {
            return "CoreBank refund succeeded but payment provider changed";
        }
        if (payment.getStatus() != PaymentStatus.CAPTURED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            return "CoreBank refund succeeded but payment is no longer refundable";
        }
        if (!Objects.equals(prepared.providerPaymentOrderId(), payment.getProviderPaymentOrderId())) {
            return "CoreBank refund succeeded but provider payment order changed";
        }
        BigDecimal remaining = payment.getCapturedAmount().subtract(payment.getRefundedAmount());
        if (remaining.compareTo(BigDecimal.ZERO) <= 0 || prepared.amount().compareTo(remaining) > 0) {
            return "CoreBank refund succeeded but remaining refundable amount changed";
        }
        return null;
    }

    private void markUnsafe(PaymentTransaction tx, String errorMessage, RefundResult result) {
        tx.setStatus(PaymentTransactionStatus.FAILED);
        tx.setProviderErrorCode("PAYMENT_FINALIZATION_UNSAFE");
        tx.setProviderErrorMessage(errorMessage);
        tx.setProviderJournalId(result.providerJournalId());
        tx.setProviderResponse(result.providerMetadataJson());
        paymentTransactionRepository.save(tx);
    }

    private void markFailed(PreparedRefundContext prepared, String providerErrorCode, String providerErrorMessage) {
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
        throw new PaymentNotFoundException(String.valueOf(booking.getId()));
    }

    private void validateRefund(BookingPayment payment, BigDecimal amount) {
        if (payment.getProvider() != PaymentProviderType.COREBANK) {
            throw new BusinessRuleException("PAYMENT_PROVIDER_UNSUPPORTED", "Refund is supported only for CoreBank");
        }
        if (payment.getStatus() != PaymentStatus.CAPTURED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BusinessRuleException("PAYMENT_INVALID_STATUS", "Payment is not in refundable status");
        }
        if (payment.getProviderPaymentOrderId() == null || payment.getProviderPaymentOrderId().isBlank()) {
            throw new BusinessRuleException("PAYMENT_PROVIDER_REFERENCE_MISSING", "Payment is missing provider payment order reference");
        }
        BigDecimal remaining = payment.getCapturedAmount().subtract(payment.getRefundedAmount());
        if (amount.compareTo(remaining) > 0) {
            throw new BusinessRuleException("PAYMENT_REFUND_AMOUNT_EXCEEDED", "Refund amount exceeds captured amount");
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

    private record PreparedRefundContext(
            UUID paymentId,
            UUID bookingId,
            UUID transactionId,
            BigDecimal amount,
            String providerPaymentOrderId,
            RefundCommand command
    ) {
    }

    private record FinalizedPaymentDetail(
            PaymentDetailResponse response,
            boolean unsafe,
            String unsafeReason
    ) {
    }

    private record RefundHashInput(
            UUID paymentId,
            BigDecimal amount,
            String reason
    ) {
    }
}
