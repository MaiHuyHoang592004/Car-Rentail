package com.rentflow.dispute.service;

import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.booking.repository.BookingTimelineEntryRepository;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.ResourceNotFoundException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.common.web.PageResponse;
import com.rentflow.dispute.dto.CreateDisputeRequest;
import com.rentflow.dispute.dto.AdminDisputeDetailResponse;
import com.rentflow.dispute.dto.DisputeResponse;
import com.rentflow.dispute.dto.ResolveDisputeRequest;
import com.rentflow.dispute.entity.Dispute;
import com.rentflow.dispute.entity.DisputeAttachment;
import com.rentflow.dispute.entity.DisputeCategory;
import com.rentflow.dispute.entity.DisputeStatus;
import com.rentflow.dispute.repository.DisputeAttachmentRepository;
import com.rentflow.dispute.repository.DisputeRepository;
import com.rentflow.file.service.FileService;
import com.rentflow.payment.dto.RefundPaymentRequest;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final DisputeAttachmentRepository disputeAttachmentRepository;
    private final BookingRepository bookingRepository;
    private final BookingPaymentRepository bookingPaymentRepository;
    private final BookingTimelineEntryRepository bookingTimelineEntryRepository;
    private final SecurityContext securityContext;
    private final FileService fileService;
    private final PaymentService paymentService;
    private final Clock clock;

    @Autowired
    public DisputeService(
            DisputeRepository disputeRepository,
            DisputeAttachmentRepository disputeAttachmentRepository,
            BookingRepository bookingRepository,
            BookingPaymentRepository bookingPaymentRepository,
            BookingTimelineEntryRepository bookingTimelineEntryRepository,
            SecurityContext securityContext,
            FileService fileService,
            PaymentService paymentService,
            Clock clock) {
        this.disputeRepository = disputeRepository;
        this.disputeAttachmentRepository = disputeAttachmentRepository;
        this.bookingRepository = bookingRepository;
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.bookingTimelineEntryRepository = bookingTimelineEntryRepository;
        this.securityContext = securityContext;
        this.fileService = fileService;
        this.paymentService = paymentService;
        this.clock = clock;
    }

    public DisputeService(
            DisputeRepository disputeRepository,
            BookingRepository bookingRepository,
            SecurityContext securityContext,
            Clock clock) {
        this(
                disputeRepository,
                null,
                bookingRepository,
                null,
                null,
                securityContext,
                null,
                null,
                clock);
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
        dispute.setCategory(request.category() == null ? DisputeCategory.OTHER : request.category());
        dispute.setReason(request.reason().trim());
        dispute.setContext(normalize(request.context()));
        dispute = disputeRepository.save(dispute);
        List<UUID> attachmentFileIds = normalizeAttachmentIds(request.attachmentFileIds());
        for (UUID fileId : attachmentFileIds) {
            if (fileService != null) {
                fileService.requireAttachableDisputeFile(fileId, customerId);
            }
            DisputeAttachment attachment = new DisputeAttachment();
            attachment.setDisputeId(dispute.getId());
            attachment.setFileId(fileId);
            if (disputeAttachmentRepository != null) {
                disputeAttachmentRepository.save(attachment);
            }
        }
        return DisputeResponse.from(dispute, attachmentFileIds);
    }

    @Transactional(readOnly = true)
    public PageResponse<DisputeResponse> listDisputes(DisputeStatus status, Pageable pageable) {
        Page<Dispute> page = status == null
                ? disputeRepository.findAll(pageable)
                : disputeRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public DisputeResponse getDispute(UUID disputeId) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("DISPUTE_NOT_FOUND", "Dispute", disputeId.toString()));
        return toResponse(dispute);
    }

    @Transactional(readOnly = true)
    public AdminDisputeDetailResponse getAdminDisputeDetail(UUID disputeId) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("DISPUTE_NOT_FOUND", "Dispute", disputeId.toString()));
        Booking booking = bookingRepository.findById(dispute.getBookingId())
                .orElseThrow(() -> new BookingNotFoundException(dispute.getBookingId().toString()));
        BookingPayment payment = bookingPaymentRepository.findByBookingId(booking.getId()).orElse(null);
        return new AdminDisputeDetailResponse(
                toResponse(dispute),
                AdminDisputeDetailResponse.BookingContext.from(booking),
                AdminDisputeDetailResponse.PaymentContext.from(payment),
                bookingTimelineEntryRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId()).stream()
                        .map(AdminDisputeDetailResponse.TimelineEntry::from)
                        .toList());
    }

    @Transactional
    public DisputeResponse resolveDispute(UUID disputeId, ResolveDisputeRequest request) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("DISPUTE_NOT_FOUND", "Dispute", disputeId.toString()));
        if (dispute.getStatus() == DisputeStatus.RESOLVED) {
            throw new BusinessRuleException("DISPUTE_INVALID_STATUS", "Dispute is already resolved");
        }
        if ("REFUND".equalsIgnoreCase(request.refundAction())) {
            if (request.paymentId() == null || request.refundAmount() == null) {
                throw new BusinessRuleException("DISPUTE_REFUND_INVALID", "paymentId and refundAmount are required for refund resolution");
            }
            if (paymentService != null) {
                paymentService.refundPayment(
                        request.paymentId(),
                        UUID.randomUUID().toString(),
                        new RefundPaymentRequest(request.refundAmount(), request.resolutionNote().trim()));
            }
            dispute.setRefundAction("REFUND");
            dispute.setRefundPaymentId(request.paymentId());
            dispute.setRefundAmount(request.refundAmount());
        } else {
            dispute.setRefundAction(request.refundAction());
        }
        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolutionNote(request.resolutionNote().trim());
        dispute.setResolvedBy(securityContext.currentUserId());
        dispute.setResolvedAt(clock.instant());
        dispute = disputeRepository.save(dispute);
        return toResponse(dispute);
    }

    private DisputeResponse toResponse(Dispute dispute) {
        if (disputeAttachmentRepository == null) {
            return DisputeResponse.from(dispute, List.of());
        }
        List<UUID> attachmentFileIds = disputeAttachmentRepository.findByDisputeId(dispute.getId()).stream()
                .map(DisputeAttachment::getFileId)
                .toList();
        return DisputeResponse.from(dispute, attachmentFileIds);
    }

    private List<UUID> normalizeAttachmentIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return new LinkedHashSet<>(ids).stream().toList();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
