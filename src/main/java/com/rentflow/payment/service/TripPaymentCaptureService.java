package com.rentflow.payment.service;

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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TripPaymentCaptureService {

    private final BookingPaymentRepository bookingPaymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentProviderRouter paymentProviderRouter;
    private final CorrelationIdHelper correlationIdHelper;

    public TripPaymentCaptureService(
            BookingPaymentRepository bookingPaymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            PaymentProviderRouter paymentProviderRouter,
            CorrelationIdHelper correlationIdHelper) {
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentProviderRouter = paymentProviderRouter;
        this.correlationIdHelper = correlationIdHelper;
    }

    @Transactional
    public void captureRemainingForBooking(UUID bookingId) {
        BookingPayment payment = bookingPaymentRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new PaymentNotFoundException(bookingId.toString()));
        if (payment.getProvider() != PaymentProviderType.COREBANK) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            return;
        }
        BigDecimal remainingAmount = payment.getAuthorizedAmount().subtract(payment.getCapturedAmount());
        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

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

        try {
            CaptureResult result = paymentProviderRouter.route(PaymentProviderType.COREBANK).capture(new CaptureCommand(
                    "rentflow:trip:checkout:capture:" + payment.getId() + ":" + requestId,
                    payment.getProviderPaymentOrderId(),
                    remainingAmount,
                    payment.getCurrency(),
                    correlationId,
                    requestId,
                    payment.getId().toString(),
                    correlationId));
            tx.setStatus(PaymentTransactionStatus.SUCCEEDED);
            tx.setProviderJournalId(result.providerJournalId());
            tx.setProviderResponse(result.providerMetadataJson());
            paymentTransactionRepository.save(tx);

            payment.setCapturedAmount(payment.getCapturedAmount().add(remainingAmount));
            payment.setStatus(PaymentStatus.CAPTURED);
            payment.setProviderStatus(result.providerStatus());
            payment.setProviderMetadata(result.providerMetadataJson());
            bookingPaymentRepository.save(payment);
        } catch (PaymentProviderUnavailableException e) {
            tx.setStatus(PaymentTransactionStatus.FAILED);
            tx.setProviderErrorCode("PAYMENT_PROVIDER_UNAVAILABLE");
            tx.setProviderErrorMessage(e.getMessage());
            paymentTransactionRepository.save(tx);
            throw new PaymentException("PAYMENT_FAILED", "Payment capture failed during check-out", e);
        } catch (RuntimeException e) {
            tx.setStatus(PaymentTransactionStatus.FAILED);
            tx.setProviderErrorCode("PAYMENT_PROVIDER_ERROR");
            tx.setProviderErrorMessage(e.getMessage());
            paymentTransactionRepository.save(tx);
            throw e;
        }
    }
}
