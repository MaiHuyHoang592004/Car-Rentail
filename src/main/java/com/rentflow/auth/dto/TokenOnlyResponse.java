package com.rentflow.auth.dto;

import java.time.Instant;
import java.util.List;

public record TokenOnlyResponse(
        String tokenType,
        String accessToken,
        String accessTokenExpiresAt,
        String refreshToken,
        String refreshTokenExpiresAt
) {
    public static TokenOnlyResponse of(String accessToken, Instant accessExpiry,
                                       String refreshToken, Instant refreshExpiry) {
        return new TokenOnlyResponse(
                "Bearer",
                accessToken,
                accessExpiry.toString(),
                refreshToken,
                refreshExpiry.toString()
        );
    }
}
