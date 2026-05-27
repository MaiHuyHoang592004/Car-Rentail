package com.rentflow.payment.provider.corebank;

import com.fasterxml.jackson.annotation.JsonAlias;

public record CoreBankCaptureHoldResponse(
        @JsonAlias({"paymentOrderId", "payment_order_id"}) String paymentOrderId,
        @JsonAlias({"journalId", "journal_id"}) String journalId,
        String status
) {
}
