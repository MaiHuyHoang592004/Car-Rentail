package com.rentflow.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyCleanupJob {

    private final IdempotencyCleanupProcessor processor;
    private final boolean enabled;
    private final int batchSize;

    public IdempotencyCleanupJob(
            IdempotencyCleanupProcessor processor,
            @Value("${rentflow.scheduler.idempotency-cleanup.enabled:true}") boolean enabled,
            @Value("${rentflow.scheduler.idempotency-cleanup.batch-size:500}") int batchSize) {
        this.processor = processor;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${rentflow.scheduler.idempotency-cleanup.fixed-delay-ms:3600000}")
    public void run() {
        if (!enabled) {
            return;
        }
        int deleted;
        do {
            deleted = processor.processBatch(batchSize);
        } while (deleted == batchSize);
    }
}
