package com.rentflow.payment.service;

import com.rentflow.auth.entity.Role;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.PaymentNotFoundException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.payment.dto.ReconciliationResponse;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.provider.PaymentProviderRouter;
import com.rentflow.payment.provider.ProviderOrderSnapshot;
import com.rentflow.payment.repository.BookingPaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentReconciliationService {

    private final BookingRepository bookingRepository;
    private final BookingPaymentRepository bookingPaymentRepository;
    private final SecurityContext securityContext;
    private final PaymentProviderRouter paymentProviderRouter;

    public PaymentReconciliationService(
            BookingRepository bookingRepository,
            BookingPaymentRepository bookingPaymentRepository,
            SecurityContext securityContext,
            PaymentProviderRouter paymentProviderRouter) {
        this.bookingRepository = bookingRepository;
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.securityContext = securityContext;
        this.paymentProviderRouter = paymentProviderRouter;
    }

    @Transactional
    public ReconciliationResponse reconcile(UUID paymentId) {
        UUID actorId = securityContext.currentUserId();

        BookingPayment paymentForLookup = bookingPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(paymentId)));
        Booking booking = bookingRepository.findById(paymentForLookup.getBookingId())
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(paymentId)));
        requireCanReconcile(actorId, booking);
        if (paymentForLookup.getProvider() != PaymentProviderType.COREBANK) {
            throw new BusinessRuleException(
                    "PAYMENT_PROVIDER_UNSUPPORTED",
                    "Reconciliation is supported only for CoreBank");
        }
        if (paymentForLookup.getExternalOrderRef() == null || paymentForLookup.getExternalOrderRef().isBlank()) {
            throw new BusinessRuleException(
                    "PAYMENT_PROVIDER_REFERENCE_MISSING",
                    "Payment is missing external order reference");
        }

        ProviderOrderSnapshot provider = paymentProviderRouter.route(PaymentProviderType.COREBANK)
                .findByExternalOrderRef(paymentForLookup.getExternalOrderRef());

        BookingPayment payment = bookingPaymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(paymentId)));

        ReconciliationResponse.MismatchFlags mismatchFlags = new ReconciliationResponse.MismatchFlags(
                statusMismatch(payment, provider),
                amountMismatch(payment, provider),
                referenceMismatch(payment, provider));
        boolean requiresReconciliation = mismatchFlags.statusMismatch()
                || mismatchFlags.amountMismatch()
                || mismatchFlags.referenceMismatch();

        if (requiresReconciliation) {
            payment.setStatus(PaymentStatus.RECONCILIATION_REQUIRED);
            bookingPaymentRepository.save(payment);
        }

        return new ReconciliationResponse(
                new ReconciliationResponse.LocalSnapshot(
                        payment.getId(),
                        payment.getBookingId(),
                        payment.getProvider(),
                        payment.getStatus(),
                        payment.getProviderStatus(),
                        payment.getExternalOrderRef(),
                        payment.getProviderPaymentOrderId(),
                        payment.getProviderHoldId(),
                        payment.getAuthorizedAmount(),
                        payment.getCapturedAmount(),
                        payment.getRefundedAmount(),
                        payment.getCurrency()),
                new ReconciliationResponse.ProviderSnapshot(
                        provider.status(),
                        provider.paymentOrderId(),
                        provider.holdId(),
                        provider.authorizedAmount(),
                        provider.capturedAmount(),
                        provider.refundedAmount(),
                        provider.currency()),
                mismatchFlags,
                requiresReconciliation);
    }

    private void requireCanReconcile(UUID actorId, Booking booking) {
        if (securityContext.hasRole(Role.ADMIN)) {
            return;
        }
        if (securityContext.hasRole(Role.HOST) && booking.getHostId().equals(actorId)) {
            return;
        }
        throw new PaymentNotFoundException(String.valueOf(booking.getId()));
    }

    private boolean statusMismatch(BookingPayment payment, ProviderOrderSnapshot provider) {
        if (provider.status() == null) {
            return false;
        }
        if (payment.getProviderStatus() == null) {
            return true;
        }
        return !provider.status().equalsIgnoreCase(payment.getProviderStatus());
    }

    private boolean amountMismatch(BookingPayment payment, ProviderOrderSnapshot provider) {
        boolean authorizedMismatch = compareAmount(payment.getAuthorizedAmount(), provider.authorizedAmount());
        boolean capturedMismatch = compareAmount(payment.getCapturedAmount(), provider.capturedAmount());
        boolean refundedMismatch = compareAmount(payment.getRefundedAmount(), provider.refundedAmount());
        boolean currencyMismatch = provider.currency() != null && payment.getCurrency() != null
                && !provider.currency().equalsIgnoreCase(payment.getCurrency());
        return authorizedMismatch || capturedMismatch || refundedMismatch || currencyMismatch;
    }

    private boolean compareAmount(BigDecimal local, BigDecimal provider) {
        if (provider == null || local == null) {
            return false;
        }
        return local.compareTo(provider) != 0;
    }

    private boolean referenceMismatch(BookingPayment payment, ProviderOrderSnapshot provider) {
        boolean paymentOrderMismatch = provider.paymentOrderId() != null
                && !provider.paymentOrderId().equals(payment.getProviderPaymentOrderId());
        boolean holdMismatch = provider.holdId() != null
                && !provider.holdId().equals(payment.getProviderHoldId());
        return paymentOrderMismatch || holdMismatch;
    }
}
