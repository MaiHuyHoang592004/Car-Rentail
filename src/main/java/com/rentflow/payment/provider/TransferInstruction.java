package com.rentflow.payment.provider;

import java.math.BigDecimal;

public record TransferInstruction(
        String bankCode,
        String bankBin,
        String accountNumber,
        String accountName,
        BigDecimal amount,
        String content,
        String qrPayload
) {
}
