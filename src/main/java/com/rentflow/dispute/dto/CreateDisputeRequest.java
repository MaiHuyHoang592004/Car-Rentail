package com.rentflow.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDisputeRequest(
        @NotBlank @Size(max = 1000) String reason) {
}
