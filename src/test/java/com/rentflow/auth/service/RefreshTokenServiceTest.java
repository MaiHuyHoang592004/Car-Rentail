package com.rentflow.auth.service;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.RefreshToken;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.RefreshTokenRepository;
import com.rentflow.common.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final String RAW_TOKEN = "refresh-token";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtTokenProvider tokenProvider;

    @Test
    void findUsableTokenReturnsActiveUnexpiredToken() {
        RefreshTokenService service = new RefreshTokenService(refreshTokenRepository, tokenProvider);
        RefreshToken token = activeToken(service);
        when(refreshTokenRepository.findByTokenHash(service.sha256(RAW_TOKEN))).thenReturn(Optional.of(token));

        RefreshToken result = service.findUsableTokenOrRevokeFamilyOnReuse(RAW_TOKEN);

        assertThat(result).isSameAs(token);
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
    }

    @Test
    void reusedRotatedRefreshTokenRevokesActiveFamilyTokens() {
        RefreshTokenService service = new RefreshTokenService(refreshTokenRepository, tokenProvider);
        RefreshToken token = activeToken(service);
        token.setRevokedAt(Instant.now().minusSeconds(60));
        token.setReplacedByTokenId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        when(refreshTokenRepository.findByTokenHash(service.sha256(RAW_TOKEN))).thenReturn(Optional.of(token));

        RefreshToken result = service.findUsableTokenOrRevokeFamilyOnReuse(RAW_TOKEN);

        assertThat(result).isNull();
        verify(refreshTokenRepository).revokeAllByUserId(eq(USER_ID), any());
    }

    @Test
    void revokedLogoutTokenDoesNotRevokeFamilyWhenItWasNotRotated() {
        RefreshTokenService service = new RefreshTokenService(refreshTokenRepository, tokenProvider);
        RefreshToken token = activeToken(service);
        token.setRevokedAt(Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByTokenHash(service.sha256(RAW_TOKEN))).thenReturn(Optional.of(token));

        RefreshToken result = service.findUsableTokenOrRevokeFamilyOnReuse(RAW_TOKEN);

        assertThat(result).isNull();
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
    }

    private RefreshToken activeToken(RefreshTokenService service) {
        AuthUser user = new AuthUser("user@example.com", "hash", UserStatus.ACTIVE, true);
        user.setId(USER_ID);
        return new RefreshToken(user, service.sha256(RAW_TOKEN), Instant.now().plusSeconds(3600));
    }
}
