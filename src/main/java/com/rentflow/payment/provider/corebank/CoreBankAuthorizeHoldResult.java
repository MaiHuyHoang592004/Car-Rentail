package com.rentflow.payment.provider.corebank;

public record CoreBankAuthorizeHoldResult(
        CoreBankAuthorizeHoldResponse response,
        String rawResponseJson
) {
}
