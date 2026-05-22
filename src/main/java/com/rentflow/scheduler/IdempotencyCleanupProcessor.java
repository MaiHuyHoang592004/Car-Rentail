package com.rentflow.scheduler;

import com.rentflow.common.idempotency.repository.IdempotencyKeyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Component
public class IdempotencyCleanupProcessor {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final Clock clock;

    public IdempotencyCleanupProcessor(
            IdempotencyKeyRepository idempotencyKeyRepository,
            Clock clock) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.clock = clock;
    }

    @Transactional
    public int processBatch(int batchSize) {
        int deleted = idempotencyKeyRepository.deleteExpiredKeys(clock.instant(), batchSize);
        if (deleted > 0) {
            log.info("Deleted {} expired idempotency keys", deleted);
        }
        return deleted;
    }
}
