package com.rentflow.scheduler;

import com.rentflow.payment.service.PaymentVoidRetryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VoidRetryJob {

    private final PaymentVoidRetryService paymentVoidRetryService;
    private final boolean enabled;
    private final int batchSize;

    public VoidRetryJob(
            PaymentVoidRetryService paymentVoidRetryService,
            @Value("${rentflow.scheduler.void-retry.enabled:true}") boolean enabled,
            @Value("${rentflow.scheduler.void-retry.batch-size:50}") int batchSize) {
        this.paymentVoidRetryService = paymentVoidRetryService;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${rentflow.scheduler.void-retry.fixed-delay-ms:60000}")
    public void run() {
        if (!enabled) {
            return;
        }
        paymentVoidRetryService.processBatch(batchSize);
    }
}
