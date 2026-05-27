package com.rentflow.payment.provider.stub;

import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.provider.AuthorizeCommand;
import com.rentflow.payment.provider.AuthorizeResult;
import com.rentflow.payment.provider.CaptureCommand;
import com.rentflow.payment.provider.CaptureResult;
import com.rentflow.payment.provider.PaymentProvider;
import com.rentflow.payment.provider.ProviderOrderSnapshot;
import com.rentflow.payment.provider.RefundCommand;
import com.rentflow.payment.provider.RefundResult;
import com.rentflow.payment.provider.TransferInstruction;
import com.rentflow.payment.provider.VoidCommand;
import com.rentflow.payment.provider.VoidResult;
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

    @Override
    public CaptureResult capture(CaptureCommand command) {
        throw new UnsupportedOperationException("StubPaymentProvider does not support capture");
    }

    @Override
    public VoidResult voidAuthorization(VoidCommand command) {
        throw new UnsupportedOperationException("StubPaymentProvider does not support void");
    }

    @Override
    public RefundResult refund(RefundCommand command) {
        throw new UnsupportedOperationException("StubPaymentProvider does not support refund");
    }

    @Override
    public ProviderOrderSnapshot findByExternalOrderRef(String externalOrderRef) {
        throw new UnsupportedOperationException("StubPaymentProvider does not support reconciliation lookup");
    }
}
