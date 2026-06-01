package com.rentflow.outbox.service;

import com.rentflow.outbox.entity.OutboxEvent;
import com.rentflow.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventDispatcher outboxEventDispatcher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public OutboxPublisherService(
            OutboxEventRepository outboxEventRepository,
            OutboxEventDispatcher outboxEventDispatcher,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxEventDispatcher = outboxEventDispatcher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public void processBatch(int batchSize, int maxAttempts, int backoffSeconds) {
        Instant now = clock.instant();
        List<OutboxEvent> candidates = transactionTemplate.execute(status ->
                claimCandidates(now, batchSize, backoffSeconds));
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (OutboxEvent event : candidates) {
            publishSingle(event.getId(), maxAttempts, backoffSeconds);
        }
    }

    private List<OutboxEvent> claimCandidates(Instant now, int batchSize, int backoffSeconds) {
        List<OutboxEvent> candidates = outboxEventRepository.findPublishCandidatesForUpdate(now, batchSize);
        Instant visibilityDeadline = now.plusSeconds(Math.max(60, backoffSeconds));
        for (OutboxEvent event : candidates) {
            event.setStatus("PROCESSING");
            event.setNextAttemptAt(visibilityDeadline);
            outboxEventRepository.save(event);
        }
        return candidates;
    }

    private void publishSingle(UUID eventId, int maxAttempts, int backoffSeconds) {
        OutboxEvent event = transactionTemplate.execute(status ->
                outboxEventRepository.findByIdForUpdate(eventId).orElse(null));
        if (event == null || !"PROCESSING".equals(event.getStatus())) {
            return;
        }

        try {
            outboxEventDispatcher.dispatch(event);
            transactionTemplate.executeWithoutResult(status -> markSent(eventId));
        } catch (RuntimeException ex) {
            transactionTemplate.executeWithoutResult(status ->
                    markFailed(eventId, ex, maxAttempts, backoffSeconds));
        }
    }

    private void markSent(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(eventId).orElse(null);
        if (event == null || !"PROCESSING".equals(event.getStatus())) {
            return;
        }
        event.setStatus("SENT");
        event.setSentAt(clock.instant());
        event.setNextAttemptAt(null);
        event.setLastError(null);
        outboxEventRepository.save(event);
    }

    private void markFailed(UUID eventId, RuntimeException ex, int maxAttempts, int backoffSeconds) {
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(eventId).orElse(null);
        if (event == null || !"PROCESSING".equals(event.getStatus())) {
            return;
        }
        int attempts = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
        event.setRetryCount(attempts);
        event.setLastError(trimError(ex.getMessage()));
        if (attempts >= maxAttempts) {
            event.setStatus("FAILED");
            event.setNextAttemptAt(null);
        } else {
            event.setStatus("RETRY");
            event.setNextAttemptAt(clock.instant().plusSeconds(calculateBackoffSeconds(backoffSeconds, attempts)));
        }
        outboxEventRepository.save(event);
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
