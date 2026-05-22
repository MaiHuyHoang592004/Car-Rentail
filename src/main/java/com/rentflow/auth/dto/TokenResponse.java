package com.rentflow.auth.dto;

import java.time.Instant;
import java.util.List;

public record TokenResponse(
        String tokenType,
        String accessToken,
        String accessTokenExpiresAt,
        String refreshToken,
        String refreshTokenExpiresAt,
        AuthUserProfileResponse user
) {
    public static TokenResponse of(String accessToken, Instant accessExpiry,
                                  String refreshToken, Instant refreshExpiry,
                                  AuthUserProfileResponse user) {
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
