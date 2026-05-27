package com.rentflow.payment.dto;

import java.util.List;

public record PaymentBankListResponse(List<PaymentBankResponse> items) {
}
