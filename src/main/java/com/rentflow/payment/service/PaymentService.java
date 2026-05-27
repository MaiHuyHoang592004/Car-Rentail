package com.rentflow.payment.service;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.payment.dto.AuthorizePaymentRequest;
import com.rentflow.payment.dto.AuthorizePaymentResponse;
import com.rentflow.payment.dto.CapturePaymentRequest;
import com.rentflow.payment.dto.PaymentDetailResponse;
import com.rentflow.payment.dto.ReconciliationResponse;
import com.rentflow.payment.dto.RefundPaymentRequest;
import com.rentflow.payment.entity.PaymentBank;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.repository.PaymentBankRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentBankRepository paymentBankRepository;
    private final BankTransferAuthorizeService bankTransferAuthorizeService;
    private final CoreBankAuthorizeService coreBankAuthorizeService;
    private final PaymentQueryService paymentQueryService;
    private final CoreBankCaptureService coreBankCaptureService;
    private final CoreBankVoidService coreBankVoidService;
    private final CoreBankRefundService coreBankRefundService;
    private final PaymentReconciliationService paymentReconciliationService;

    public PaymentService(
            PaymentBankRepository paymentBankRepository,
            BankTransferAuthorizeService bankTransferAuthorizeService,
            CoreBankAuthorizeService coreBankAuthorizeService,
            PaymentQueryService paymentQueryService,
            CoreBankCaptureService coreBankCaptureService,
            CoreBankVoidService coreBankVoidService,
            CoreBankRefundService coreBankRefundService,
            PaymentReconciliationService paymentReconciliationService) {
        this.paymentBankRepository = paymentBankRepository;
        this.bankTransferAuthorizeService = bankTransferAuthorizeService;
        this.coreBankAuthorizeService = coreBankAuthorizeService;
        this.paymentQueryService = paymentQueryService;
        this.coreBankCaptureService = coreBankCaptureService;
        this.coreBankVoidService = coreBankVoidService;
        this.coreBankRefundService = coreBankRefundService;
        this.paymentReconciliationService = paymentReconciliationService;
    }

    public AuthorizePaymentResponse authorizeBookingPayment(
            UUID bookingId,
            String idempotencyKey,
            AuthorizePaymentRequest request) {
        PaymentBank bank = paymentBankRepository.findByIdAndActiveTrue(request.bankId())
                .orElseThrow(() -> new ValidationException("Selected payment bank is not available"));

        if (bank.getProvider() == PaymentProviderType.COREBANK) {
            return coreBankAuthorizeService.authorizeBookingPayment(bookingId, idempotencyKey, request, bank);
        }
        return bankTransferAuthorizeService.authorizeBookingPayment(bookingId, idempotencyKey, request, bank);
    }

    public PaymentDetailResponse getBookingPayment(UUID bookingId) {
        return paymentQueryService.getByBookingId(bookingId);
    }

    public PaymentDetailResponse capturePayment(UUID paymentId, String idempotencyKey, CapturePaymentRequest request) {
        return coreBankCaptureService.capture(paymentId, idempotencyKey, request);
    }

    public PaymentDetailResponse voidPayment(UUID paymentId, String idempotencyKey) {
        return coreBankVoidService.voidAuthorization(paymentId, idempotencyKey);
    }

    public PaymentDetailResponse refundPayment(UUID paymentId, String idempotencyKey, RefundPaymentRequest request) {
        return coreBankRefundService.refund(paymentId, idempotencyKey, request);
    }

    public ReconciliationResponse reconcilePayment(UUID paymentId) {
        return paymentReconciliationService.reconcile(paymentId);
    }
}
