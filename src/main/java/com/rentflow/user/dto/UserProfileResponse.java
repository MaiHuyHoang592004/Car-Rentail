package com.rentflow.user.dto;

import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.user.entity.UserProfile;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
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
    public static UserProfileResponse from(
            UUID id,
            String email,
            boolean emailVerified,
            List<Role> roles,
            UserProfile profile) {
        return new UserProfileResponse(
                id,
                email,
                emailVerified,
                roles.stream().map(Role::name).toList(),
                profile.getFullName(),
                profile.getPhone(),
                profile.getDateOfBirth(),
                profile.getAddressLine(),
                profile.getDriverVerificationStatus().name()
        );
    }
}
