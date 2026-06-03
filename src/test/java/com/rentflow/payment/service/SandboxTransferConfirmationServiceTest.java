package com.rentflow.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.auth.entity.Role;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.booking.service.AvailabilityReserver;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.payment.dto.PaymentDetailResponse;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SandboxTransferConfirmationServiceTest {

    private static final UUID BOOKING_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CUSTOMER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OTHER_USER_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID IDEMPOTENCY_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final String IDEMPOTENCY_KEY = "8b71f8d2-9e1d-4f7a-bbe6-334c3816df91";

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
                mock(IdempotencyService.class),
                mock(IdempotencyFailureMarker.class),
                false);

        assertThatThrownBy(() -> service.confirm(UUID.randomUUID(), UUID.randomUUID().toString()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SANDBOX_PAYMENT_DISABLED");
    }

    @Test
    void confirmReplayReturnsStoredResponseWithoutBusinessLogic() throws Exception {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        IdempotencyService idempotencyService = mock(IdempotencyService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PaymentDetailResponse replayed = new PaymentDetailResponse(
                new PaymentDetailResponse.BookingSummary(
                        BOOKING_ID,
                        CUSTOMER_ID,
                        OTHER_USER_ID,
                        BookingStatus.HELD,
                        null,
                        null),
                new PaymentDetailResponse.PaymentSummary(
                        UUID.fromString("55555555-5555-4555-8555-555555555555"),
                        null,
                        null,
                        null,
                        PaymentStatus.PENDING_TRANSFER,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "VND",
                        null,
                        null,
                        null,
                        null,
                        null),
                List.of());
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(
                CUSTOMER_ID,
                IdempotencyScope.SIMULATE_TRANSFER_CONFIRMATION,
                IDEMPOTENCY_KEY,
                "hash"))
                .thenReturn(IdempotencyResolution.replay(200, objectMapper.writeValueAsString(replayed)));

        SandboxTransferConfirmationService service = service(
                bookingRepository,
                mock(BookingPaymentRepository.class),
                mock(PaymentTransactionRepository.class),
                mock(AvailabilityReserver.class),
                securityContext,
                mock(PaymentBookingSnapshotParser.class),
                mock(PaymentDetailResponseFactory.class),
                objectMapper,
                idempotencyService,
                mock(IdempotencyFailureMarker.class),
                true);

        PaymentDetailResponse result = service.confirm(BOOKING_ID, IDEMPOTENCY_KEY);

        assertThat(result.booking().id()).isEqualTo(BOOKING_ID);
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.PENDING_TRANSFER);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void confirmRejectsNonOwnerAsBookingNotFoundAndMarksIdempotencyFailed() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        IdempotencyService idempotencyService = mock(IdempotencyService.class);
        IdempotencyFailureMarker idempotencyFailureMarker = mock(IdempotencyFailureMarker.class);
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setCustomerId(CUSTOMER_ID);
        booking.setStatus(BookingStatus.HELD);

        when(securityContext.currentUserId()).thenReturn(OTHER_USER_ID);
        when(securityContext.hasRole(Role.ADMIN)).thenReturn(false);
        when(idempotencyService.computeHash(any())).thenReturn("hash");
        when(idempotencyService.resolve(
                OTHER_USER_ID,
                IdempotencyScope.SIMULATE_TRANSFER_CONFIRMATION,
                IDEMPOTENCY_KEY,
                "hash"))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));

        SandboxTransferConfirmationService service = service(
                bookingRepository,
                mock(BookingPaymentRepository.class),
                mock(PaymentTransactionRepository.class),
                mock(AvailabilityReserver.class),
                securityContext,
                mock(PaymentBookingSnapshotParser.class),
                mock(PaymentDetailResponseFactory.class),
                new ObjectMapper(),
                idempotencyService,
                idempotencyFailureMarker,
                true);

        assertThatThrownBy(() -> service.confirm(BOOKING_ID, IDEMPOTENCY_KEY))
                .isInstanceOf(BookingNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "BOOKING_NOT_FOUND");
        verify(idempotencyFailureMarker).markFailed(IDEMPOTENCY_ID);
    }

    private SandboxTransferConfirmationService service(
            BookingRepository bookingRepository,
            BookingPaymentRepository bookingPaymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            AvailabilityReserver availabilityReserver,
            SecurityContext securityContext,
            PaymentBookingSnapshotParser bookingSnapshotParser,
            PaymentDetailResponseFactory paymentDetailResponseFactory,
            ObjectMapper objectMapper,
            IdempotencyService idempotencyService,
            IdempotencyFailureMarker idempotencyFailureMarker,
            boolean enabled) {
        return new SandboxTransferConfirmationService(
                bookingRepository,
                bookingPaymentRepository,
                paymentTransactionRepository,
                availabilityReserver,
                securityContext,
                bookingSnapshotParser,
                paymentDetailResponseFactory,
                objectMapper,
                Clock.fixed(Instant.parse("2026-05-11T00:00:00Z"), ZoneOffset.UTC),
                idempotencyService,
                idempotencyFailureMarker,
                enabled);
    }
}
