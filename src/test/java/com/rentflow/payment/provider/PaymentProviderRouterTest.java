package com.rentflow.payment.provider;

import com.rentflow.payment.config.BankTransferProperties;
import com.rentflow.payment.config.CoreBankPaymentProperties;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.provider.banktransfer.BankTransferQrProvider;
import com.rentflow.payment.provider.corebank.CoreBankPaymentClient;
import com.rentflow.payment.provider.corebank.CoreBankPaymentProvider;
import com.rentflow.payment.provider.stub.StubPaymentProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PaymentProviderRouterTest {

    @Test
    void routeSelectsBankTransferQrProviderForVietQrManual() {
        PaymentProviderRouter router = new PaymentProviderRouter(List.of(
                new BankTransferQrProvider(properties()),
                new StubPaymentProvider()));

        PaymentProvider provider = router.route(PaymentProviderType.VIETQR_MANUAL);

        assertThat(provider).isInstanceOf(BankTransferQrProvider.class);
    }

    @Test
    void routeSelectsStubPaymentProviderForStub() {
        PaymentProviderRouter router = new PaymentProviderRouter(List.of(
                new BankTransferQrProvider(properties()),
                new StubPaymentProvider()));

        PaymentProvider provider = router.route(PaymentProviderType.STUB);

        assertThat(provider).isInstanceOf(StubPaymentProvider.class);
    }

    @Test
    void routeSelectsCoreBankPaymentProviderForCoreBank() {
        PaymentProviderRouter router = new PaymentProviderRouter(List.of(
                new BankTransferQrProvider(properties()),
                new StubPaymentProvider(),
                new CoreBankPaymentProvider(mock(CoreBankPaymentClient.class), coreBankProperties(), new com.fasterxml.jackson.databind.ObjectMapper())));

        PaymentProvider provider = router.route(PaymentProviderType.COREBANK);

        assertThat(provider).isInstanceOf(CoreBankPaymentProvider.class);
    }

    private BankTransferProperties properties() {
        BankTransferProperties properties = new BankTransferProperties();
        properties.setAccountNumber("1234567890");
        properties.setAccountName("RENTFLOW ESCROW");
        properties.setTransferContentPrefix("RENTFLOW");
        return properties;
    }

    private CoreBankPaymentProperties coreBankProperties() {
        CoreBankPaymentProperties properties = new CoreBankPaymentProperties();
        properties.setPayeeAccountId("rentflow-escrow-corebank-account-id");
        return properties;
    }
}
