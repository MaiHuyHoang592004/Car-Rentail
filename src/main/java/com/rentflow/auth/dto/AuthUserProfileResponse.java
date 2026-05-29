package com.rentflow.auth.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AuthUserProfileResponse(
        UUID id,
        String email,
        boolean emailVerified,
        List<String> roles,
        String fullName,
        String phone,
        LocalDate dateOfBirth,
        String addressLine,
        String driverVerificationStatus
) {
}
