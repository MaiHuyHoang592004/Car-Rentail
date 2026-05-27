package com.rentflow.payment.provider.corebank;

import com.fasterxml.jackson.annotation.JsonAlias;

public record CoreBankAuthorizeHoldResponse(
        @JsonAlias({"paymentOrderId", "payment_order_id"}) String paymentOrderId,
        @JsonAlias({"holdId", "hold_id"}) String holdId,
        String status
) {
}
