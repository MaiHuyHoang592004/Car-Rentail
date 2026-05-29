package com.rentflow.auth.service;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.EmailVerificationToken;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.EmailVerificationTokenRepository;
import com.rentflow.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final Duration TOKEN_LIFETIME = Duration.ofDays(1);

    private final AuthUserRepository authUserRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Transactional
    public void sendVerification(UUID userId) {
        AuthUser user = authUserRepository.findById(userId).orElseThrow();
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }
        String raw = generateOpaqueToken();
        String hash = PasswordService.sha256(raw);
        emailVerificationTokenRepository.save(new EmailVerificationToken(
                user.getId(), hash, Instant.now().plus(TOKEN_LIFETIME)));
        log.info("[email-stub] verification requested for {}", user.getEmail());
    }

    @Transactional
    public void verify(String rawToken) {
        String hash = PasswordService.sha256(rawToken);
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessRuleException(
                        "INVALID_TOKEN", "Verification token is invalid or expired"));
        if (!token.isUsable()) {
            throw new BusinessRuleException("INVALID_TOKEN", "Verification token is invalid or expired");
        }
        AuthUser user = authUserRepository.findById(token.getUserId())
                .orElseThrow(() -> new BusinessRuleException(
                        "INVALID_TOKEN", "Verification token is invalid or expired"));

        user.setEmailVerified(true);
        authUserRepository.save(user);

        token.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(token);

        log.info("Email verified for user {}", user.getEmail());
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
