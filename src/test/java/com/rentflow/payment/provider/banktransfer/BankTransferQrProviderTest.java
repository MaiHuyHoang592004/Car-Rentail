package com.rentflow.payment.provider.banktransfer;

import com.rentflow.booking.entity.Booking;
import com.rentflow.payment.config.BankTransferProperties;
import com.rentflow.payment.entity.PaymentBank;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.provider.AuthorizeCommand;
import com.rentflow.payment.provider.AuthorizeResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BankTransferQrProviderTest {

    @Test
    void authorizeBuildsTransferInstructionWithoutAuthorizingMoney() {
        BankTransferProperties properties = new BankTransferProperties();
        properties.setAccountNumber("1234567890");
        properties.setAccountName("RENTFLOW ESCROW");
        properties.setTransferContentPrefix("RENTFLOW");
        BankTransferQrProvider provider = new BankTransferQrProvider(properties);

        Booking booking = new Booking();
        booking.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        PaymentBank bank = new PaymentBank();
        bank.setCode("VCB");
        bank.setBin("970436");
        bank.setPaymentMethod(PaymentMethod.BANK_TRANSFER_QR);
        bank.setProvider(PaymentProviderType.VIETQR_MANUAL);

        AuthorizeResult result = provider.authorize(new AuthorizeCommand(
                booking,
                bank,
                PaymentMethod.BANK_TRANSFER_QR,
                new BigDecimal("1400000.00"),
                "VND",
                "rentflow:booking:" + booking.getId(),
                null,
                null,
                null,
                null,
                null,
                null));

        assertThat(result.provider()).isEqualTo(PaymentProviderType.VIETQR_MANUAL);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.PENDING_TRANSFER);
        assertThat(result.authorizedAmount()).isEqualByComparingTo("0");
        assertThat(result.transferInstruction()).isNotNull();
        assertThat(result.transferInstruction().accountNumber()).isEqualTo("1234567890");
        assertThat(result.transferInstruction().content()).isEqualTo("RENTFLOW " + booking.getId());
        assertThat(result.transferInstruction().qrPayload()).startsWith("manual-vietqr:");
        assertThat(result.providerPaymentOrderId()).isNull();
        assertThat(result.providerHoldId()).isNull();
    }
}
