package com.rentflow.auth.service;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.RefreshToken;
import com.rentflow.auth.repository.RefreshTokenRepository;
import com.rentflow.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public CreatedRefreshToken createRefreshToken(AuthUser user) {
        String rawToken = generateOpaqueToken();
        String hash = sha256(rawToken);

        Instant expiresAt = tokenProvider.getRefreshTokenExpiry();

        RefreshToken token = new RefreshToken(user, hash, expiresAt);
        return new CreatedRefreshToken(rawToken, refreshTokenRepository.save(token));
    }

    @Transactional(readOnly = true)
    public RefreshToken findActiveByToken(String rawToken) {
        String hash = sha256(rawToken);
        return refreshTokenRepository.findActiveByTokenHash(hash, Instant.now())
                .orElse(null);
    }

    @Transactional
    public CreatedRefreshToken rotateRefreshToken(RefreshToken oldToken) {
        Instant now = Instant.now();

        oldToken.setRevokedAt(now);
        refreshTokenRepository.save(oldToken);

        AuthUser user = oldToken.getUser();
        String rawToken = generateOpaqueToken();
        String hash = sha256(rawToken);
        Instant expiresAt = tokenProvider.getRefreshTokenExpiry();

        RefreshToken newToken = new RefreshToken(user, hash, expiresAt);
        newToken = refreshTokenRepository.save(newToken);

        oldToken.setReplacedByTokenId(newToken.getId());
        refreshTokenRepository.save(oldToken);

        return new CreatedRefreshToken(rawToken, newToken);
    }

    @Transactional
    public void revokeByToken(String rawToken) {
        String hash = sha256(rawToken);
        refreshTokenRepository.findByTokenHash(hash)
                .ifPresent(token -> {
                    if (token.getRevokedAt() == null) {
                        token.setRevokedAt(Instant.now());
                        refreshTokenRepository.save(token);
                    }
                });
    }

    public String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record CreatedRefreshToken(String rawToken, RefreshToken token) {
    }
}
