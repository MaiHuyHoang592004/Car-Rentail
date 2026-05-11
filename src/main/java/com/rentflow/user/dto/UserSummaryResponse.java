package com.rentflow.user.dto;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.user.entity.UserProfile;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String email,
        List<String> roles,
        String fullName,
        String status,
        String driverVerificationStatus,
        Instant createdAt,
        Instant lastLoginAt
) {
    public static UserSummaryResponse from(AuthUser user, UserProfile profile, List<Role> roles) {
        return new UserSummaryResponse(
                user.getId(),
                user.getEmail(),
                roles.stream().map(Role::name).toList(),
                profile.getFullName(),
                user.getStatus().name(),
                profile.getDriverVerificationStatus().name(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}
