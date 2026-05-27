package com.rentflow.payment.provider.corebank;

public record CoreBankCaptureHoldResult(
        CoreBankCaptureHoldResponse response,
        String rawResponseJson
) {
}
