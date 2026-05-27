package com.rentflow.payment.dto;

import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;

import java.util.UUID;

public record PaymentBankResponse(
        UUID id,
        String code,
        String bin,
        String shortName,
        String fullName,
        PaymentMethod paymentMethod,
        PaymentProviderType provider,
        boolean active
) {
}
