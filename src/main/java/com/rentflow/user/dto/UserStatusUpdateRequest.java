package com.rentflow.user.dto;

import jakarta.validation.constraints.Size;

public record UserStatusUpdateRequest(
        @Size(max = 500) String reason) {
}
