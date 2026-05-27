package com.rentflow.payment.provider.banktransfer;

import com.rentflow.payment.config.BankTransferProperties;
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
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class BankTransferQrProvider implements PaymentProvider {

    private final BankTransferProperties properties;

    public BankTransferQrProvider(BankTransferProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.VIETQR_MANUAL;
    }

    @Override
    public AuthorizeResult authorize(AuthorizeCommand command) {
        String content = properties.getTransferContentPrefix() + " " + command.booking().getId();
        TransferInstruction transferInstruction = new TransferInstruction(
                command.bank().getCode(),
                command.bank().getBin(),
                properties.getAccountNumber(),
                properties.getAccountName(),
                command.totalAmount(),
                content,
                qrPayload(command.bank().getCode(), command.bank().getBin(), command.totalAmount(), content));

        return new AuthorizeResult(
                PaymentProviderType.VIETQR_MANUAL,
                PaymentStatus.PENDING_TRANSFER,
                BigDecimal.ZERO,
                transferInstruction,
                "TRANSFER_INSTRUCTION_GENERATED",
                null,
                null,
                null);
    }

    // This is a deterministic local/manual payload, not settlement confirmation.
    private String qrPayload(String bankCode, String bankBin, BigDecimal amount, String content) {
        String rawPayload = String.join("|",
                "manual-vietqr",
                bankCode == null ? "" : bankCode,
                bankBin == null ? "" : bankBin,
                properties.getAccountNumber(),
                properties.getAccountName(),
                amount.toPlainString(),
                content);
        return "manual-vietqr:"
                + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rawPayload.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public CaptureResult capture(CaptureCommand command) {
        throw new UnsupportedOperationException("BankTransferQrProvider does not support capture");
    }

    @Override
    public VoidResult voidAuthorization(VoidCommand command) {
        throw new UnsupportedOperationException("BankTransferQrProvider does not support void");
    }

    @Override
    public RefundResult refund(RefundCommand command) {
        throw new UnsupportedOperationException("BankTransferQrProvider does not support refund");
    }

    @Override
    public ProviderOrderSnapshot findByExternalOrderRef(String externalOrderRef) {
        throw new UnsupportedOperationException("BankTransferQrProvider does not support reconciliation lookup");
    }
}
