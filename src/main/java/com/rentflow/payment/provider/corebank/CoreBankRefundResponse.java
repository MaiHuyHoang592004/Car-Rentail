package com.rentflow.payment.provider.corebank;

import com.fasterxml.jackson.annotation.JsonAlias;

public record CoreBankRefundResponse(
        @JsonAlias({"paymentOrderId", "payment_order_id"}) String paymentOrderId,
        @JsonAlias({"refundJournalId", "refund_journal_id", "journalId", "journal_id"}) String refundJournalId,
        String status
) {
}
