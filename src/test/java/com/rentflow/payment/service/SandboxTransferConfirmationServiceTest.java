package com.rentflow.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.booking.service.AvailabilityReserver;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SandboxTransferConfirmationServiceTest {

    @Test
    void confirmRejectsWhenSandboxConfirmationIsDisabled() {
        SandboxTransferConfirmationService service = new SandboxTransferConfirmationService(
                mock(BookingRepository.class),
                mock(BookingPaymentRepository.class),
                mock(PaymentTransactionRepository.class),
                mock(AvailabilityReserver.class),
                mock(SecurityContext.class),
                mock(PaymentBookingSnapshotParser.class),
                mock(PaymentDetailResponseFactory.class),
                new ObjectMapper(),
                Clock.systemUTC(),
                false);

        assertThatThrownBy(() -> service.confirm(UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SANDBOX_PAYMENT_DISABLED");
    }
}
