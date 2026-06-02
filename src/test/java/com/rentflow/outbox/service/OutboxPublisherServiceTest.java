package com.rentflow.outbox.service;

import com.rentflow.outbox.entity.OutboxEvent;
import com.rentflow.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private OutboxEventDispatcher outboxEventDispatcher;

    private OutboxPublisherService service;
    private AtomicInteger transactionCommits;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-29T00:00:00Z"), ZoneOffset.UTC);
        transactionCommits = new AtomicInteger();
        service = new OutboxPublisherService(outboxEventRepository, outboxEventDispatcher, clock, transactionManager());
    }

    @Test
    void successfulDispatchMarksEventAsSent() {
        OutboxEvent event = event("PENDING", 0);
        when(outboxEventRepository.findPublishCandidatesForClaim(
                Instant.parse("2026-05-29T00:00:00Z"),
                Instant.parse("2026-05-28T23:55:00Z"),
                10))
                .thenReturn(List.of(event));
        when(outboxEventRepository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            assertThat(transactionCommits).hasValue(1);
            assertThat(event.getStatus()).isEqualTo("PROCESSING");
            assertThat(event.getProcessingStartedAt()).isEqualTo(Instant.parse("2026-05-29T00:00:00Z"));
            assertThat(event.getClaimedBy()).startsWith("rentflow-outbox-");
            return null;
        }).when(outboxEventDispatcher).dispatch(event);

        service.processBatch(10, 5, 60, 300);

        verify(outboxEventRepository, times(2)).save(event);
        assertThat(transactionCommits).hasValue(2);
        assertThat(event.getStatus()).isEqualTo("SENT");
        assertThat(event.getSentAt()).isEqualTo(Instant.parse("2026-05-29T00:00:00Z"));
        assertThat(event.getProcessingStartedAt()).isNull();
        assertThat(event.getClaimedBy()).isNull();
    }

    @Test
    void failedDispatchSchedulesRetryAndThenFailsAtMaxAttempts() {
        OutboxEvent retryEvent = event("PENDING", 0);
        OutboxEvent exhaustedEvent = event("RETRY", 4);
        when(outboxEventRepository.findPublishCandidatesForClaim(
                Instant.parse("2026-05-29T00:00:00Z"),
                Instant.parse("2026-05-28T23:55:00Z"),
                10))
                .thenReturn(List.of(retryEvent, exhaustedEvent));
        when(outboxEventRepository.findByIdForUpdate(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            if (retryEvent.getId().equals(id)) {
                return Optional.of(retryEvent);
            }
            if (exhaustedEvent.getId().equals(id)) {
                return Optional.of(exhaustedEvent);
            }
            return Optional.empty();
        });
        doThrow(new RuntimeException("dispatcher down")).when(outboxEventDispatcher).dispatch(any(OutboxEvent.class));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.processBatch(10, 5, 60, 300);

        verify(outboxEventRepository, times(4)).save(any(OutboxEvent.class));
        assertThat(retryEvent.getStatus()).isEqualTo("RETRY");
        assertThat(retryEvent.getRetryCount()).isEqualTo(1);
        assertThat(retryEvent.getNextAttemptAt()).isEqualTo(Instant.parse("2026-05-29T00:01:00Z"));
        assertThat(retryEvent.getProcessingStartedAt()).isNull();
        assertThat(retryEvent.getClaimedBy()).isNull();
        assertThat(exhaustedEvent.getStatus()).isEqualTo("FAILED");
        assertThat(exhaustedEvent.getRetryCount()).isEqualTo(5);
        assertThat(exhaustedEvent.getNextAttemptAt()).isNull();
        assertThat(exhaustedEvent.getProcessingStartedAt()).isNull();
        assertThat(exhaustedEvent.getClaimedBy()).isNull();
    }

    @Test
    void staleProcessingEventIsReclaimedAndDispatched() {
        OutboxEvent staleEvent = event("PROCESSING", 1);
        staleEvent.setProcessingStartedAt(Instant.parse("2026-05-28T23:00:00Z"));
        staleEvent.setClaimedBy("old-worker");
        when(outboxEventRepository.findPublishCandidatesForClaim(
                Instant.parse("2026-05-29T00:00:00Z"),
                Instant.parse("2026-05-28T23:55:00Z"),
                10))
                .thenReturn(List.of(staleEvent));
        when(outboxEventRepository.findByIdForUpdate(staleEvent.getId())).thenReturn(Optional.of(staleEvent));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.processBatch(10, 5, 60, 300);

        verify(outboxEventDispatcher).dispatch(staleEvent);
        assertThat(staleEvent.getStatus()).isEqualTo("SENT");
        assertThat(staleEvent.getClaimedBy()).isNull();
        assertThat(staleEvent.getProcessingStartedAt()).isNull();
    }

    private OutboxEvent event(String status, int retryCount) {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setStatus(status);
        event.setRetryCount(retryCount);
        event.setEventType("BOOKING_CONFIRMED");
        event.setAggregateType("BOOKING");
        return event;
    }

    private PlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                transactionCommits.incrementAndGet();
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
    }
}
