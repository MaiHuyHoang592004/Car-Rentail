package com.rentflow.integration.idempotency;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.common.idempotency.entity.IdempotencyKey;
import com.rentflow.common.idempotency.entity.IdempotencyStatus;
import com.rentflow.common.idempotency.repository.IdempotencyKeyRepository;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.scheduler.IdempotencyCleanupProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class IdempotencyCleanupIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IdempotencyKeyRepository repository;

    @Autowired
    private IdempotencyCleanupProcessor processor;

    @Autowired
    private AuthUserRepository authUserRepository;

    private UUID userId;

    @BeforeEach
    void cleanSlate() {
        repository.deleteAll();
        authUserRepository.deleteAll();
        AuthUser user = new AuthUser(
                "idem-cleanup-" + UUID.randomUUID() + "@example.com",
                "$2a$10$abcdefghijklmnopqrstuv",
                UserStatus.ACTIVE,
                false);
        userId = authUserRepository.save(user).getId();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
        authUserRepository.deleteAll();
    }

    @Test
    @DisplayName("deleteExpiredKeys removes rows whose expires_at is in the past")
    void deleteExpiredKeys_removesOnlyExpired() {
        Instant now = Instant.now();

        IdempotencyKey expired = saveKey("expired-key", "hash-1", now.minusSeconds(30));
        IdempotencyKey active = saveKey("active-key", "hash-2", now.plusSeconds(60));

        int deleted = processor.processBatch(100);

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findById(expired.getId())).isEmpty();
        assertThat(repository.findById(active.getId())).isPresent();
    }

    @Test
    @DisplayName("processBatch is a no-op when nothing is expired")
    void processBatch_noop_whenNothingExpired() {
        Instant now = Instant.now();
        saveKey("fresh-key", "hash", now.plusSeconds(3600));

        assertThat(processor.processBatch(100)).isZero();
        assertThat(repository.count()).isEqualTo(1);
    }

    private IdempotencyKey saveKey(String key, String hash, Instant expiresAt) {
        IdempotencyKey row = new IdempotencyKey();
        row.setUserId(userId);
        row.setScope(IdempotencyScope.CREATE_BOOKING);
        row.setKey(key);
        row.setRequestHash(hash);
        row.setStatus(IdempotencyStatus.COMPLETED);
        row.setExpiresAt(expiresAt);
        return repository.save(row);
    }
}
