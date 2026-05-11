package com.rentflow.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpireHeldBookingsJob {

    private final ExpireHeldBookingsProcessor processor;
    private final boolean enabled;
    private final int batchSize;

    public ExpireHeldBookingsJob(
            ExpireHeldBookingsProcessor processor,
            @Value("${rentflow.scheduler.expire-held-bookings.enabled:true}") boolean enabled,
            @Value("${rentflow.scheduler.expire-held-bookings.batch-size:100}") int batchSize) {
        this.processor = processor;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${rentflow.scheduler.expire-held-bookings.fixed-delay-ms:60000}")
    public void run() {
        if (!enabled) {
            return;
        }
        processor.processBatch(batchSize);
    }
}
