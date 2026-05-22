package com.rentflow.auth.dto;

import java.util.List;
import java.util.UUID;

public record RegisterResponse(
        UUID id,
        String email,
        List<String> roles,
        String fullName,
        String status,
        String driverVerificationStatus
) {
}
