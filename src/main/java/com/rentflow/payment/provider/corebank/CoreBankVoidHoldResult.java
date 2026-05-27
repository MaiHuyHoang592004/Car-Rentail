package com.rentflow.payment.provider.corebank;

public record CoreBankVoidHoldResult(
        CoreBankVoidHoldResponse response,
        String rawResponseJson
) {
}
