package com.rentflow.dispute.dto;

import com.rentflow.dispute.entity.DisputeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateDisputeRequest(
        DisputeCategory category,
        @NotBlank @Size(max = 1000) String reason,
        @Size(max = 2000) String context,
        List<UUID> attachmentFileIds) {
    public CreateDisputeRequest {
        if (category == null) {
            category = DisputeCategory.OTHER;
        }
        if (attachmentFileIds == null) {
            attachmentFileIds = List.of();
        }
    }

    public CreateDisputeRequest(String reason) {
        this(DisputeCategory.OTHER, reason, null, List.of());
    }
}
