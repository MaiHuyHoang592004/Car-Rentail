package com.rentflow.outbox.service;

import com.rentflow.outbox.entity.OutboxEvent;
import com.rentflow.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventDispatcher outboxEventDispatcher;
    private final Clock clock;

    public OutboxPublisherService(
            OutboxEventRepository outboxEventRepository,
            OutboxEventDispatcher outboxEventDispatcher,
            Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxEventDispatcher = outboxEventDispatcher;
        this.clock = clock;
    }

    @Transactional
    public void processBatch(int batchSize, int maxAttempts, int backoffSeconds) {
        Instant now = clock.instant();
        List<OutboxEvent> candidates = outboxEventRepository.findPublishCandidatesForUpdate(now, batchSize);
        for (OutboxEvent event : candidates) {
            publishSingle(event, now, maxAttempts, backoffSeconds);
        }
    }

    private void publishSingle(OutboxEvent event, Instant now, int maxAttempts, int backoffSeconds) {
        try {
            outboxEventDispatcher.dispatch(event);
            event.setStatus("SENT");
            event.setSentAt(now);
            event.setNextAttemptAt(null);
            event.setLastError(null);
            outboxEventRepository.save(event);
        } catch (RuntimeException ex) {
            int attempts = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
            event.setRetryCount(attempts);
            event.setLastError(trimError(ex.getMessage()));
            if (attempts >= maxAttempts) {
                event.setStatus("FAILED");
                event.setNextAttemptAt(null);
            } else {
                event.setStatus("RETRY");
                event.setNextAttemptAt(now.plusSeconds(calculateBackoffSeconds(backoffSeconds, attempts)));
            }
            outboxEventRepository.save(event);
        }
    }

    private long calculateBackoffSeconds(int baseBackoffSeconds, int attempt) {
        long multiplier = 1L << Math.max(0, attempt - 1);
        long calculated = (long) baseBackoffSeconds * multiplier;
        return Math.min(calculated, 24L * 60 * 60);
    }

    private String trimError(String error) {
        if (error == null) {
            return "unknown";
        }
        return error.length() > 2000 ? error.substring(0, 2000) : error;
    }
}
