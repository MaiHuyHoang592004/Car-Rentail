package com.rentflow.payment.service;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.payment.dto.AuthorizePaymentRequest;
import com.rentflow.payment.dto.AuthorizePaymentResponse;
import com.rentflow.payment.entity.PaymentBank;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.repository.PaymentBankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final UUID BOOKING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BANK_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private PaymentBankRepository paymentBankRepository;
    @Mock private BankTransferAuthorizeService bankTransferAuthorizeService;
    @Mock private CoreBankAuthorizeService coreBankAuthorizeService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentBankRepository, bankTransferAuthorizeService, coreBankAuthorizeService);
    }

    @Test
    void routesCoreBankAuthorizeToCoreBankService() {
        AuthorizePaymentRequest request = new AuthorizePaymentRequest(BANK_ID, PaymentMethod.COREBANK_TRANSFER);
        PaymentBank bank = bank(PaymentMethod.COREBANK_TRANSFER, PaymentProviderType.COREBANK);
        AuthorizePaymentResponse response = mock(AuthorizePaymentResponse.class);
        when(paymentBankRepository.findByIdAndActiveTrue(BANK_ID)).thenReturn(Optional.of(bank));
        when(coreBankAuthorizeService.authorizeBookingPayment(BOOKING_ID, "key", request, bank)).thenReturn(response);

        paymentService.authorizeBookingPayment(BOOKING_ID, "key", request);

        verify(coreBankAuthorizeService).authorizeBookingPayment(BOOKING_ID, "key", request, bank);
    }

    @Test
    void routesManualBanksToBankTransferAuthorizeService() {
        AuthorizePaymentRequest request = new AuthorizePaymentRequest(BANK_ID, PaymentMethod.BANK_TRANSFER_QR);
        PaymentBank bank = bank(PaymentMethod.BANK_TRANSFER_QR, PaymentProviderType.VIETQR_MANUAL);
        AuthorizePaymentResponse response = mock(AuthorizePaymentResponse.class);
        when(paymentBankRepository.findByIdAndActiveTrue(BANK_ID)).thenReturn(Optional.of(bank));
        when(bankTransferAuthorizeService.authorizeBookingPayment(BOOKING_ID, "key", request, bank)).thenReturn(response);

        paymentService.authorizeBookingPayment(BOOKING_ID, "key", request);

        verify(bankTransferAuthorizeService).authorizeBookingPayment(BOOKING_ID, "key", request, bank);
    }

    @Test
    void rejectsInactiveOrMissingBank() {
        when(paymentBankRepository.findByIdAndActiveTrue(BANK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.authorizeBookingPayment(
                BOOKING_ID,
                "key",
                new AuthorizePaymentRequest(BANK_ID, PaymentMethod.BANK_TRANSFER_QR)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Selected payment bank is not available");
    }

    private PaymentBank bank(PaymentMethod paymentMethod, PaymentProviderType providerType) {
        PaymentBank bank = new PaymentBank();
        bank.setId(BANK_ID);
        bank.setCode("BANK");
        bank.setPaymentMethod(paymentMethod);
        bank.setProvider(providerType);
        bank.setActive(true);
        return bank;
    }
}
