package com.rentflow.payment.service;

import com.rentflow.payment.dto.PaymentBankListResponse;
import com.rentflow.payment.dto.PaymentBankResponse;
import com.rentflow.payment.repository.PaymentBankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentBankService {

    private final PaymentBankRepository paymentBankRepository;

    @Transactional(readOnly = true)
    public PaymentBankListResponse listActiveBanks() {
        return new PaymentBankListResponse(paymentBankRepository.findByActiveTrueOrderByDisplayOrderAscShortNameAsc()
                .stream()
                .map(bank -> new PaymentBankResponse(
                        bank.getId(),
                        bank.getCode(),
                        bank.getBin(),
                        bank.getShortName(),
                        bank.getFullName(),
                        bank.getPaymentMethod(),
                        bank.getProvider(),
                        Boolean.TRUE.equals(bank.getActive())))
                .toList());
    }
}
