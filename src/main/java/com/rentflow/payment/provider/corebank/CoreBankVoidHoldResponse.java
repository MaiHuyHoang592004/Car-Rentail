package com.rentflow.payment.provider.corebank;

import com.fasterxml.jackson.annotation.JsonAlias;

public record CoreBankVoidHoldResponse(
        @JsonAlias({"holdId", "hold_id"}) String holdId,
        String status
) {
}
