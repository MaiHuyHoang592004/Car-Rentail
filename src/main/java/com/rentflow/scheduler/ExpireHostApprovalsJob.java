package com.rentflow.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpireHostApprovalsJob {

    private final ExpireHostApprovalsProcessor processor;
    private final boolean enabled;
    private final int batchSize;

    public ExpireHostApprovalsJob(
            ExpireHostApprovalsProcessor processor,
            @Value("${rentflow.scheduler.expire-host-approvals.enabled:true}") boolean enabled,
            @Value("${rentflow.scheduler.expire-host-approvals.batch-size:100}") int batchSize) {
        this.processor = processor;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${rentflow.scheduler.expire-host-approvals.fixed-delay-ms:60000}")
    public void run() {
        if (!enabled) {
            return;
        }
        processor.processBatch(batchSize);
    }
}
