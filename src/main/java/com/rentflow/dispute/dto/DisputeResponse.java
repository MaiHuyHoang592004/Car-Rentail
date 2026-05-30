package com.rentflow.dispute.dto;

import com.rentflow.dispute.entity.Dispute;
import com.rentflow.dispute.entity.DisputeCategory;
import com.rentflow.dispute.entity.DisputeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DisputeResponse(
        UUID id,
        UUID bookingId,
        UUID customerId,
        DisputeStatus status,
        DisputeCategory category,
        String reason,
        String context,
        List<UUID> attachmentFileIds,
        String resolutionNote,
        String refundAction,
        UUID refundPaymentId,
        BigDecimal refundAmount,
        UUID resolvedBy,
        Instant resolvedAt,
        Instant createdAt) {

    public DisputeResponse(
            UUID id,
            UUID bookingId,
            UUID customerId,
            DisputeStatus status,
            String reason,
            String resolutionNote,
            UUID resolvedBy,
            Instant resolvedAt,
            Instant createdAt) {
        this(
                id,
                bookingId,
                customerId,
                status,
                DisputeCategory.OTHER,
                reason,
                null,
                List.of(),
                resolutionNote,
                null,
                null,
                null,
                resolvedBy,
                resolvedAt,
                createdAt);
    }

    public static DisputeResponse from(Dispute dispute) {
        return from(dispute, List.of());
    }

    public static DisputeResponse from(Dispute dispute, List<UUID> attachmentFileIds) {
        return new DisputeResponse(
                dispute.getId(),
                dispute.getBookingId(),
                dispute.getCustomerId(),
                dispute.getStatus(),
                dispute.getCategory(),
                dispute.getReason(),
                dispute.getContext(),
                attachmentFileIds,
                dispute.getResolutionNote(),
                dispute.getRefundAction(),
                dispute.getRefundPaymentId(),
                dispute.getRefundAmount(),
                dispute.getResolvedBy(),
                dispute.getResolvedAt(),
                dispute.getCreatedAt());
    }
}
