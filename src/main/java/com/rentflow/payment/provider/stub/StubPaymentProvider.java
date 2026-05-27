package com.rentflow.payment.provider.stub;

import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.provider.AuthorizeCommand;
import com.rentflow.payment.provider.AuthorizeResult;
import com.rentflow.payment.provider.PaymentProvider;
import com.rentflow.payment.provider.TransferInstruction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StubPaymentProvider implements PaymentProvider {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.STUB;
    }

    @Override
    public AuthorizeResult authorize(AuthorizeCommand command) {
        TransferInstruction transferInstruction = new TransferInstruction(
                command.bank().getCode(),
                command.bank().getBin(),
                "stub-account-number",
                "Stub Beneficiary",
                command.totalAmount(),
                "STUB " + command.booking().getId(),
                "stub:" + command.booking().getId());

        return new AuthorizeResult(
                PaymentProviderType.STUB,
                PaymentStatus.PENDING_TRANSFER,
                BigDecimal.ZERO,
                transferInstruction,
                "STUB_TRANSFER_INSTRUCTION_GENERATED",
                null,
                null,
                null);
    }
}
