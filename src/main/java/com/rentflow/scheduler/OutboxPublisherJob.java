package com.rentflow.scheduler;

import com.rentflow.outbox.service.OutboxPublisherService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisherJob {

    private final OutboxPublisherService outboxPublisherService;
    private final boolean enabled;
    private final int batchSize;
    private final int maxAttempts;
    private final int backoffSeconds;
    private final int processingTimeoutSeconds;

    public OutboxPublisherJob(
            OutboxPublisherService outboxPublisherService,
            @Value("${rentflow.scheduler.outbox-publisher.enabled:true}") boolean enabled,
            @Value("${rentflow.scheduler.outbox-publisher.batch-size:100}") int batchSize,
            @Value("${rentflow.scheduler.outbox-publisher.max-attempts:5}") int maxAttempts,
            @Value("${rentflow.scheduler.outbox-publisher.backoff-seconds:60}") int backoffSeconds,
            @Value("${rentflow.scheduler.outbox-publisher.processing-timeout-seconds:300}") int processingTimeoutSeconds) {
        this.outboxPublisherService = outboxPublisherService;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.backoffSeconds = backoffSeconds;
        this.processingTimeoutSeconds = processingTimeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${rentflow.scheduler.outbox-publisher.fixed-delay-ms:60000}")
    public void run() {
        if (!enabled) {
            return;
        }
        outboxPublisherService.processBatch(batchSize, maxAttempts, backoffSeconds, processingTimeoutSeconds);
    }
}
