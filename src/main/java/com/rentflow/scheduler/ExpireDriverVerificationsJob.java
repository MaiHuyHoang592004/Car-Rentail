package com.rentflow.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpireDriverVerificationsJob {

    private final ExpireDriverVerificationsProcessor processor;
    private final boolean enabled;
    private final int batchSize;

    public ExpireDriverVerificationsJob(
            ExpireDriverVerificationsProcessor processor,
            @Value("${rentflow.scheduler.expire-driver-verifications.enabled:true}") boolean enabled,
            @Value("${rentflow.scheduler.expire-driver-verifications.batch-size:100}") int batchSize) {
        this.processor = processor;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${rentflow.scheduler.expire-driver-verifications.cron:0 0 0 * * *}", zone = "UTC")
    public void run() {
        if (!enabled) {
            return;
        }
        processor.processBatch(batchSize);
    }
}
