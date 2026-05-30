package com.rentflow.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ResolveDisputeRequest(
        @NotBlank @Size(max = 1000) String resolutionNote,
        String refundAction,
        UUID paymentId,
        BigDecimal refundAmount) {
    public ResolveDisputeRequest(String resolutionNote) {
        this(resolutionNote, null, null, null);
    }
}
