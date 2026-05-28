package com.rentflow.dispute.service;

import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.ResourceNotFoundException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.common.web.PageResponse;
import com.rentflow.dispute.dto.CreateDisputeRequest;
import com.rentflow.dispute.dto.DisputeResponse;
import com.rentflow.dispute.dto.ResolveDisputeRequest;
import com.rentflow.dispute.entity.Dispute;
import com.rentflow.dispute.entity.DisputeStatus;
import com.rentflow.dispute.repository.DisputeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final BookingRepository bookingRepository;
    private final SecurityContext securityContext;
    private final Clock clock;

    public DisputeService(
            DisputeRepository disputeRepository,
            BookingRepository bookingRepository,
            SecurityContext securityContext,
            Clock clock) {
        this.disputeRepository = disputeRepository;
        this.bookingRepository = bookingRepository;
        this.securityContext = securityContext;
        this.clock = clock;
    }

    @Transactional
    public DisputeResponse createDispute(UUID bookingId, CreateDisputeRequest request) {
        UUID customerId = securityContext.currentUserId();
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        if (!booking.getCustomerId().equals(customerId)) {
            throw new BookingNotFoundException(bookingId.toString());
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Dispute is allowed only for COMPLETED bookings");
        }
        if (disputeRepository.existsByBookingIdAndCustomerId(bookingId, customerId)) {
            throw new BusinessRuleException("DISPUTE_ALREADY_EXISTS", "Dispute already exists for this booking");
        }

        Dispute dispute = new Dispute();
        dispute.setBookingId(bookingId);
        dispute.setCustomerId(customerId);
        dispute.setStatus(DisputeStatus.OPEN);
        dispute.setReason(request.reason().trim());
        dispute = disputeRepository.save(dispute);
        return DisputeResponse.from(dispute);
    }

    @Transactional(readOnly = true)
    public PageResponse<DisputeResponse> listDisputes(DisputeStatus status, Pageable pageable) {
        Page<Dispute> page = status == null
                ? disputeRepository.findAll(pageable)
                : disputeRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.from(page.map(DisputeResponse::from));
    }

    @Transactional
    public DisputeResponse resolveDispute(UUID disputeId, ResolveDisputeRequest request) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("DISPUTE_NOT_FOUND", "Dispute", disputeId.toString()));
        if (dispute.getStatus() == DisputeStatus.RESOLVED) {
            throw new BusinessRuleException("DISPUTE_INVALID_STATUS", "Dispute is already resolved");
        }
        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolutionNote(request.resolutionNote().trim());
        dispute.setResolvedBy(securityContext.currentUserId());
        dispute.setResolvedAt(clock.instant());
        dispute = disputeRepository.save(dispute);
        return DisputeResponse.from(dispute);
    }
}
