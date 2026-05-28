package com.rentflow.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveDisputeRequest(
        @NotBlank @Size(max = 1000) String resolutionNote) {
}
