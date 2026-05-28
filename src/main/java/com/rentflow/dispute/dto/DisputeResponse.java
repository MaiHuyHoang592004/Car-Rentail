package com.rentflow.dispute.dto;

import com.rentflow.dispute.entity.Dispute;
import com.rentflow.dispute.entity.DisputeStatus;

import java.time.Instant;
import java.util.UUID;

public record DisputeResponse(
        UUID id,
        UUID bookingId,
        UUID customerId,
        DisputeStatus status,
        String reason,
        String resolutionNote,
        UUID resolvedBy,
        Instant resolvedAt,
        Instant createdAt) {

    public static DisputeResponse from(Dispute dispute) {
        return new DisputeResponse(
                dispute.getId(),
                dispute.getBookingId(),
                dispute.getCustomerId(),
                dispute.getStatus(),
                dispute.getReason(),
                dispute.getResolutionNote(),
                dispute.getResolvedBy(),
                dispute.getResolvedAt(),
                dispute.getCreatedAt());
    }
}
