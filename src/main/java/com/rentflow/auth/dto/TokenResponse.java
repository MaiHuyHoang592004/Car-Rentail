package com.rentflow.auth.dto;

import com.rentflow.user.dto.UserProfileResponse;
import java.time.Instant;
import java.util.List;

public record TokenResponse(
        String tokenType,
        String accessToken,
        String accessTokenExpiresAt,
        String refreshToken,
        String refreshTokenExpiresAt,
        UserProfileResponse user
) {
    public static TokenResponse of(String accessToken, Instant accessExpiry,
                                  String refreshToken, Instant refreshExpiry,
                                  UserProfileResponse user) {
        return new TokenResponse(
                "Bearer",
                accessToken,
                accessExpiry.toString(),
                refreshToken,
                refreshExpiry.toString(),
                user
        );
    }
}
