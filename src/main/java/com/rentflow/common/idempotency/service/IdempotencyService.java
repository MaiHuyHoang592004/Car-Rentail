package com.rentflow.common.idempotency.service;

import com.rentflow.common.exception.IdempotencyException;
import com.rentflow.common.idempotency.entity.IdempotencyKey;
import com.rentflow.common.idempotency.entity.IdempotencyStatus;
import com.rentflow.common.idempotency.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Duration LOCK_DURATION = Duration.ofSeconds(30);
    private static final Duration EXPIRY_DURATION = Duration.ofDays(5);

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final Clock clock;
    private final CanonicalJsonHasher canonicalJsonHasher;

    public String computeHash(Object request) {
        return canonicalJsonHasher.hash(request);
    }

    @Transactional
    public IdempotencyResolution resolve(
            UUID userId,
            IdempotencyScope scope,
            String key,
            String requestHash) {
        Instant now = Instant.now(clock);
        Instant lockedUntil = now.plus(LOCK_DURATION);
        Instant expiresAt = now.plus(EXPIRY_DURATION);
        UUID id = UUID.randomUUID();
        String normalizedKey = normalizeKey(key);

        int inserted = idempotencyKeyRepository.insertProcessingIfAbsent(
                id, userId, scope, normalizedKey, requestHash, lockedUntil, expiresAt);
        if (inserted == 1) {
            return IdempotencyResolution.proceed(id);
        }

        IdempotencyKey existing = idempotencyKeyRepository
                .findByUserIdAndScopeAndKeyForUpdate(userId, scope, normalizedKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency key insert conflict but row was not found"));

        return resolveExisting(existing, requestHash, now, lockedUntil, expiresAt);
    }

    @Transactional
    public void complete(UUID idempotencyKeyId, int responseStatus, String responseBodyJson) {
        IdempotencyKey key = idempotencyKeyRepository.findByIdForUpdate(idempotencyKeyId)
                .orElseThrow(() -> new IllegalArgumentException("idempotency key not found"));

        key.setStatus(IdempotencyStatus.COMPLETED);
        key.setResponseStatus(responseStatus);
        key.setResponseBody(responseBodyJson);
        key.setLockedUntil(null);

        idempotencyKeyRepository.save(key);
    }

    @Transactional
    public void fail(UUID idempotencyKeyId) {
        try {
            idempotencyKeyRepository.findByIdForUpdate(idempotencyKeyId)
                    .filter(key -> key.getStatus() == IdempotencyStatus.PROCESSING)
                    .ifPresent(key -> {
                        key.setStatus(IdempotencyStatus.FAILED);
                        key.setLockedUntil(Instant.now(clock).plus(LOCK_DURATION));
                        idempotencyKeyRepository.save(key);
                    });
        } catch (RuntimeException e) {
            log.warn("Failed to mark idempotency key {} as FAILED: {}", idempotencyKeyId, e.getMessage());
        }
    }

    private IdempotencyResolution resolveExisting(
            IdempotencyKey existing,
            String requestHash,
            Instant now,
            Instant lockedUntil,
            Instant expiresAt) {
        if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
            if (!Objects.equals(existing.getRequestHash(), requestHash)) {
                throw conflict();
            }
            return IdempotencyResolution.replay(existing.getResponseStatus(), existing.getResponseBody());
        }

        if (lockIsActive(existing, now)) {
            throw alreadyProcessing();
        }

        existing.setStatus(IdempotencyStatus.PROCESSING);
        existing.setRequestHash(requestHash);
        existing.setLockedUntil(lockedUntil);
        existing.setExpiresAt(expiresAt);
        existing.setResponseStatus(null);
        existing.setResponseBody(null);

        idempotencyKeyRepository.save(existing);
        return IdempotencyResolution.proceed(existing.getId());
    }

    private boolean lockIsActive(IdempotencyKey key, Instant now) {
        return key.getLockedUntil() != null && key.getLockedUntil().isAfter(now);
    }

    private String normalizeKey(String key) {
        return key == null ? null : key.toLowerCase();
    }

    private IdempotencyException alreadyProcessing() {
        return new IdempotencyException(
                "REQUEST_ALREADY_PROCESSING",
                "Request with same idempotency key is still processing");
    }

    private IdempotencyException conflict() {
        return new IdempotencyException(
                "IDEMPOTENCY_KEY_CONFLICT",
                "Same idempotency key was used with a different request body");
    }
}
