package com.rentflow.auth.service;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * I17: Persists failed-login attempts in their own transaction so the counter
 * survives even when the outer login transaction rolls back via thrown exception.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptTracker {

    private final AuthUserRepository authUserRepository;

    public record Outcome(int attempts, Instant lockUntil) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome recordFailure(UUID userId, int threshold, Duration lockoutDuration) {
        AuthUser user = authUserRepository.findById(userId).orElse(null);
        if (user == null) {
            return new Outcome(0, null);
        }
        int attempts = user.getFailedLoginAttempts() + 1;
        if (attempts >= threshold) {
            Instant lockUntil = Instant.now().plus(lockoutDuration);
            user.setLockUntil(lockUntil);
            user.setFailedLoginAttempts(0);
            authUserRepository.save(user);
            return new Outcome(attempts, lockUntil);
        }
        user.setFailedLoginAttempts(attempts);
        authUserRepository.save(user);
        return new Outcome(attempts, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID userId) {
        AuthUser user = authUserRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        if (user.getFailedLoginAttempts() != 0 || user.getLockUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockUntil(null);
            authUserRepository.save(user);
        }
    }
}
