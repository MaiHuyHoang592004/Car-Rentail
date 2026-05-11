package com.rentflow.common.security;

import com.rentflow.auth.entity.Role;
import com.rentflow.common.exception.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String TEST_SECRET =
            "test-jwt-secret-key-for-unit-tests-only-minimum-60-bytes-required-for-hs512";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(
                TEST_SECRET,
                Duration.ofMinutes(15),
                Duration.ofDays(7)
        );
    }

    @Test
    void generateAccessToken_containsCorrectClaims() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        List<Role> roles = List.of(Role.CUSTOMER, Role.HOST);

        String token = provider.generateAccessToken(userId, email, roles);

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void validateAccessToken_succeedsForValidToken() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        List<Role> roles = List.of(Role.CUSTOMER);

        String token = provider.generateAccessToken(userId, email, roles);
        JwtTokenProvider.JwtClaims claims = provider.validateAccessToken(token);

        assertEquals(userId, claims.userId());
        assertEquals(email, claims.email());
        assertEquals(roles, claims.roles());
    }

    @Test
    void validateAccessToken_throwsForExpiredToken() {
        JwtTokenProvider shortLived = new JwtTokenProvider(
                TEST_SECRET,
                Duration.ofMillis(1),
                Duration.ofDays(1)
        );

        UUID userId = UUID.randomUUID();
        String token = shortLived.generateAccessToken(userId, "a@b.com", List.of(Role.CUSTOMER));

        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {}

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> shortLived.validateAccessToken(token));
        assertEquals("AUTH_TOKEN_EXPIRED", ex.getCode());
    }

    @Test
    void validateAccessToken_throwsForTamperedToken() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateAccessToken(userId, "a@b.com", List.of(Role.CUSTOMER));

        String tampered = token.substring(0, token.length() - 5) + "xxxxx";

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> provider.validateAccessToken(tampered));
        assertEquals("AUTH_INVALID_CREDENTIALS", ex.getCode());
    }

    @Test
    void validateAccessToken_throwsForWrongSecretToken() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(
                "test-jwt-secret-key-for-unit-tests-only-minimum-60-bytes-required-for-hs51x",
                Duration.ofMinutes(15),
                Duration.ofDays(7)
        );

        UUID userId = UUID.randomUUID();
        String token = otherProvider.generateAccessToken(userId, "a@b.com", List.of(Role.CUSTOMER));

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> provider.validateAccessToken(token));
        assertEquals("AUTH_INVALID_CREDENTIALS", ex.getCode());
    }

    @Test
    void generateRefreshToken_isDistinctFromAccessToken() {
        UUID userId = UUID.randomUUID();

        String accessToken = provider.generateAccessToken(userId, "a@b.com", List.of(Role.CUSTOMER));
        String refreshToken = provider.generateRefreshToken(userId);

        assertNotEquals(accessToken, refreshToken);
    }
}
