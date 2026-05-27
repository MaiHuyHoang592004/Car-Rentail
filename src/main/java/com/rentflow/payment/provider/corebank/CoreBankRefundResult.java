package com.rentflow.payment.provider.corebank;

public record CoreBankRefundResult(
        CoreBankRefundResponse response,
        String rawResponseJson
) {
}
