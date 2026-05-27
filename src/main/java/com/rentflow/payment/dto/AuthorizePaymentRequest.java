package com.rentflow.payment.dto;

import com.rentflow.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AuthorizePaymentRequest(
        @NotNull UUID bankId,
        @NotNull PaymentMethod paymentMethod
) {
}
