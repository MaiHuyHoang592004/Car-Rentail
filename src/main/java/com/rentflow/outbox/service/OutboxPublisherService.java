package com.rentflow.outbox.service;

import com.rentflow.outbox.entity.OutboxEvent;
import com.rentflow.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventDispatcher outboxEventDispatcher;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final String publisherId;

    public OutboxPublisherService(
            OutboxEventRepository outboxEventRepository,
            OutboxEventDispatcher outboxEventDispatcher,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxEventDispatcher = outboxEventDispatcher;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.publisherId = "rentflow-outbox-" + UUID.randomUUID();
    }

    public void processBatch(int batchSize, int maxAttempts, int backoffSeconds, int processingTimeoutSeconds) {
        Instant now = now();
        Instant staleBefore = now.minusSeconds(Math.max(1, processingTimeoutSeconds));
        List<ClaimedOutboxEvent> claimedEvents = transactionTemplate.execute(status ->
                claimBatch(now, staleBefore, batchSize));
        if (claimedEvents == null) {
            return;
        }
        for (ClaimedOutboxEvent claimedEvent : claimedEvents) {
            publishSingle(claimedEvent, maxAttempts, backoffSeconds);
        }
    }

    private List<ClaimedOutboxEvent> claimBatch(Instant now, Instant staleBefore, int batchSize) {
        return outboxEventRepository.findPublishCandidatesForClaim(now, staleBefore, batchSize).stream()
                .map(event -> {
                    event.setStatus("PROCESSING");
                    event.setProcessingStartedAt(now);
                    event.setClaimedBy(publisherId);
                    OutboxEvent claimed = outboxEventRepository.save(event);
                    return new ClaimedOutboxEvent(claimed, claimed.getId(), publisherId, now);
                })
                .toList();
    }

    private void publishSingle(ClaimedOutboxEvent claimedEvent, int maxAttempts, int backoffSeconds) {
        try {
            outboxEventDispatcher.dispatch(claimedEvent.event());
            transactionTemplate.executeWithoutResult(status -> markSent(claimedEvent, now()));
        } catch (RuntimeException ex) {
            transactionTemplate.executeWithoutResult(status ->
                    markFailed(claimedEvent, ex, now(), maxAttempts, backoffSeconds));
        }
    }

    private void markSent(ClaimedOutboxEvent claimedEvent, Instant now) {
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(claimedEvent.eventId())
                .orElse(null);
        if (!isCurrentClaim(event, claimedEvent)) {
            return;
        }
        event.setStatus("SENT");
        event.setSentAt(now);
        event.setNextAttemptAt(null);
        event.setLastError(null);
        clearClaim(event);
        outboxEventRepository.save(event);
    }

    private void markFailed(
            ClaimedOutboxEvent claimedEvent,
            RuntimeException ex,
            Instant now,
            int maxAttempts,
            int backoffSeconds) {
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(claimedEvent.eventId())
                .orElse(null);
        if (!isCurrentClaim(event, claimedEvent)) {
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
            event.setNextAttemptAt(now.plusSeconds(calculateBackoffSeconds(backoffSeconds, attempts)));
        }
        clearClaim(event);
        outboxEventRepository.save(event);
    }

    private boolean isCurrentClaim(OutboxEvent event, ClaimedOutboxEvent claimedEvent) {
        return event != null
                && "PROCESSING".equals(event.getStatus())
                && Objects.equals(claimedEvent.claimedBy(), event.getClaimedBy())
                && Objects.equals(claimedEvent.processingStartedAt(), event.getProcessingStartedAt());
    }

    private void clearClaim(OutboxEvent event) {
        event.setProcessingStartedAt(null);
        event.setClaimedBy(null);
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

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private record ClaimedOutboxEvent(
            OutboxEvent event,
            UUID eventId,
            String claimedBy,
            Instant processingStartedAt
    ) {
    }
}
