package com.rentflow.payment.service;

import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.PaymentException;
import com.rentflow.common.exception.PaymentNotFoundException;
import com.rentflow.common.exception.PaymentProviderUnavailableException;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TripPaymentCaptureService {

    private final BookingPaymentRepository bookingPaymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentProviderRouter paymentProviderRouter;
    private final CorrelationIdHelper correlationIdHelper;
    private final TransactionTemplate transactionTemplate;

    public TripPaymentCaptureService(
            BookingPaymentRepository bookingPaymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            PaymentProviderRouter paymentProviderRouter,
            CorrelationIdHelper correlationIdHelper,
            PlatformTransactionManager transactionManager) {
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentProviderRouter = paymentProviderRouter;
        this.correlationIdHelper = correlationIdHelper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void captureRemainingForBooking(UUID bookingId) {
        PreparedTripCaptureContext prepared = transactionTemplate.execute(status -> prepareCapture(bookingId));
        if (prepared == null) {
            return;
        }

        CaptureResult result;
        try {
            result = paymentProviderRouter.route(PaymentProviderType.COREBANK)
                    .capture(prepared.command());
        } catch (PaymentProviderUnavailableException e) {
            transactionTemplate.executeWithoutResult(status ->
                    markFailed(prepared, "PAYMENT_PROVIDER_UNAVAILABLE", e.getMessage()));
            throw new PaymentException("PAYMENT_FAILED", "Payment capture failed during check-out", e);
        } catch (RuntimeException e) {
            transactionTemplate.executeWithoutResult(status ->
                    markFailed(prepared, "PAYMENT_PROVIDER_ERROR", e.getMessage()));
            throw e;
        }

        transactionTemplate.executeWithoutResult(status -> finalizeCapture(prepared, result));
    }

    private PreparedTripCaptureContext prepareCapture(UUID bookingId) {
        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new PaymentNotFoundException(bookingId.toString()));
        if (!isCoreBankAuthorizedWithRemaining(payment)) {
            return null;
        }

        BigDecimal remainingAmount = payment.getAuthorizedAmount().subtract(payment.getCapturedAmount());
        String requestId = UUID.randomUUID().toString();
        String correlationId = correlationIdHelper.getOrGenerate();
        PaymentTransaction tx = new PaymentTransaction();
        tx.setBookingPaymentId(payment.getId());
        tx.setBookingId(bookingId);
        tx.setType(PaymentTransactionType.CAPTURE);
        tx.setStatus(PaymentTransactionStatus.PENDING);
        tx.setAmount(remainingAmount);
        tx.setCurrency(payment.getCurrency());
        tx.setProvider(payment.getProvider());
        tx.setProviderRef(payment.getProviderPaymentOrderId());
        tx.setProviderRequestId(requestId);
        tx = paymentTransactionRepository.save(tx);

        return new PreparedTripCaptureContext(
                payment.getId(),
                bookingId,
                tx.getId(),
                remainingAmount,
                new CaptureCommand(
                        "rentflow:trip:checkout:capture:" + payment.getId() + ":" + requestId,
                        payment.getProviderPaymentOrderId(),
                        remainingAmount,
                        payment.getCurrency(),
                        correlationId,
                        requestId,
                        payment.getId().toString(),
                        correlationId));
    }

    private void finalizeCapture(PreparedTripCaptureContext prepared, CaptureResult result) {
        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new PaymentNotFoundException(prepared.bookingId().toString()));
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new PaymentNotFoundException(prepared.paymentId().toString()));

        validateCaptureFinalizationState(prepared, payment, tx);

        BigDecimal updatedCaptured = payment.getCapturedAmount().add(prepared.remainingAmount());
        payment.setCapturedAmount(updatedCaptured);
        if (updatedCaptured.compareTo(payment.getAuthorizedAmount()) == 0) {
            payment.setStatus(PaymentStatus.CAPTURED);
        } else {
            payment.setStatus(PaymentStatus.AUTHORIZED);
        }
        payment.setProviderStatus(result.providerStatus());
        payment.setProviderMetadata(result.providerMetadataJson());
        bookingPaymentRepository.save(payment);

        tx.setStatus(PaymentTransactionStatus.SUCCEEDED);
        tx.setProviderJournalId(result.providerJournalId());
        tx.setProviderResponse(result.providerMetadataJson());
        tx.setProviderErrorCode(null);
        tx.setProviderErrorMessage(null);
        paymentTransactionRepository.save(tx);
    }

    private void validateCaptureFinalizationState(
            PreparedTripCaptureContext prepared,
            BookingPayment payment,
            PaymentTransaction tx) {
        BigDecimal remainingAmount = payment.getAuthorizedAmount().subtract(payment.getCapturedAmount());
        boolean invalid = payment.getProvider() != PaymentProviderType.COREBANK
                || payment.getStatus() != PaymentStatus.AUTHORIZED
                || payment.getProviderPaymentOrderId() == null
                || payment.getProviderPaymentOrderId().isBlank()
                || remainingAmount.compareTo(BigDecimal.ZERO) <= 0
                || prepared.remainingAmount().compareTo(remainingAmount) > 0;
        if (invalid) {
            markUnsafe(tx, "CoreBank capture succeeded but trip payment finalization was no longer safe");
            throw new BusinessRuleException(
                    "PAYMENT_FINALIZATION_UNSAFE",
                    "Payment capture succeeded but trip payment finalization was no longer safe");
        }
    }

    private boolean isCoreBankAuthorizedWithRemaining(BookingPayment payment) {
        if (payment.getProvider() != PaymentProviderType.COREBANK) {
            return false;
        }
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            return false;
        }
        if (payment.getProviderPaymentOrderId() == null || payment.getProviderPaymentOrderId().isBlank()) {
            return false;
        }
        return payment.getAuthorizedAmount().subtract(payment.getCapturedAmount()).compareTo(BigDecimal.ZERO) > 0;
    }

    private void markFailed(
            PreparedTripCaptureContext prepared,
            String errorCode,
            String errorMessage) {
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(prepared.transactionId())
                .orElseThrow(() -> new PaymentNotFoundException(prepared.paymentId().toString()));
        tx.setStatus(PaymentTransactionStatus.FAILED);
        tx.setProviderErrorCode(errorCode);
        tx.setProviderErrorMessage(errorMessage);
        paymentTransactionRepository.save(tx);
    }

    private void markUnsafe(PaymentTransaction tx, String errorMessage) {
        tx.setStatus(PaymentTransactionStatus.FAILED);
        tx.setProviderErrorCode("PAYMENT_FINALIZATION_UNSAFE");
        tx.setProviderErrorMessage(errorMessage);
        paymentTransactionRepository.save(tx);
    }

    private record PreparedTripCaptureContext(
            UUID paymentId,
            UUID bookingId,
            UUID transactionId,
            BigDecimal remainingAmount,
            CaptureCommand command
    ) {
    }
}
