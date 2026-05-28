package com.rentflow.dispute.service;

import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.dispute.dto.CreateDisputeRequest;
import com.rentflow.dispute.dto.DisputeResponse;
import com.rentflow.dispute.dto.ResolveDisputeRequest;
import com.rentflow.dispute.entity.Dispute;
import com.rentflow.dispute.entity.DisputeStatus;
import com.rentflow.dispute.repository.DisputeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private SecurityContext securityContext;

    private DisputeService disputeService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-29T00:00:00Z"), ZoneOffset.UTC);
        disputeService = new DisputeService(disputeRepository, bookingRepository, securityContext, clock);
    }

    @Test
    void createDisputeForCompletedBookingSucceeds() {
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setCustomerId(customerId);
        booking.setStatus(BookingStatus.COMPLETED);

        when(securityContext.currentUserId()).thenReturn(customerId);
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(disputeRepository.existsByBookingIdAndCustomerId(bookingId, customerId)).thenReturn(false);
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> {
            Dispute dispute = invocation.getArgument(0);
            dispute.setId(UUID.randomUUID());
            return dispute;
        });

        DisputeResponse response = disputeService.createDispute(bookingId, new CreateDisputeRequest("Late car issue"));
        assertThat(response.status()).isEqualTo(DisputeStatus.OPEN);
        assertThat(response.bookingId()).isEqualTo(bookingId);
    }

    @Test
    void createDisputeRejectsNonCompletedBooking() {
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setCustomerId(customerId);
        booking.setStatus(BookingStatus.CONFIRMED);

        when(securityContext.currentUserId()).thenReturn(customerId);
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> disputeService.createDispute(bookingId, new CreateDisputeRequest("Invalid status")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void resolveDisputeChangesStatusToResolved() {
        UUID disputeId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Dispute dispute = new Dispute();
        dispute.setId(disputeId);
        dispute.setStatus(DisputeStatus.OPEN);
        when(securityContext.currentUserId()).thenReturn(adminId);
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DisputeResponse response = disputeService.resolveDispute(disputeId, new ResolveDisputeRequest("Refund approved"));
        assertThat(response.status()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(response.resolvedBy()).isEqualTo(adminId);
    }
}
