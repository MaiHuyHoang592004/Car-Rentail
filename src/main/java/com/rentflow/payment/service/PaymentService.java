package com.rentflow.payment.service;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.payment.dto.AuthorizePaymentRequest;
import com.rentflow.payment.dto.AuthorizePaymentResponse;
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

    public PaymentService(
            PaymentBankRepository paymentBankRepository,
            BankTransferAuthorizeService bankTransferAuthorizeService,
            CoreBankAuthorizeService coreBankAuthorizeService) {
        this.paymentBankRepository = paymentBankRepository;
        this.bankTransferAuthorizeService = bankTransferAuthorizeService;
        this.coreBankAuthorizeService = coreBankAuthorizeService;
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
}
