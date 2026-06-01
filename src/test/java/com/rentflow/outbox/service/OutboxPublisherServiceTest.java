package com.rentflow.outbox.service;

import com.rentflow.outbox.entity.OutboxEvent;
import com.rentflow.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private OutboxEventDispatcher outboxEventDispatcher;

    private OutboxPublisherService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-29T00:00:00Z"), ZoneOffset.UTC);
        service = new OutboxPublisherService(outboxEventRepository, outboxEventDispatcher, testTransactionManager(), clock);
    }

    @Test
    void successfulDispatchMarksEventAsSent() {
        OutboxEvent event = event("PENDING", 0);
        when(outboxEventRepository.findPublishCandidatesForUpdate(Instant.parse("2026-05-29T00:00:00Z"), 10))
                .thenReturn(List.of(event));
        when(outboxEventRepository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.processBatch(10, 5, 60);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(event.getStatus()).isEqualTo("SENT");
        assertThat(event.getSentAt()).isEqualTo(Instant.parse("2026-05-29T00:00:00Z"));
    }

    @Test
    void failedDispatchSchedulesRetryAndThenFailsAtMaxAttempts() {
        OutboxEvent retryEvent = event("PENDING", 0);
        OutboxEvent exhaustedEvent = event("RETRY", 4);
        when(outboxEventRepository.findPublishCandidatesForUpdate(Instant.parse("2026-05-29T00:00:00Z"), 10))
                .thenReturn(List.of(retryEvent, exhaustedEvent));
        when(outboxEventRepository.findByIdForUpdate(retryEvent.getId())).thenReturn(Optional.of(retryEvent));
        when(outboxEventRepository.findByIdForUpdate(exhaustedEvent.getId())).thenReturn(Optional.of(exhaustedEvent));
        doThrow(new RuntimeException("dispatcher down")).when(outboxEventDispatcher).dispatch(any(OutboxEvent.class));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.processBatch(10, 5, 60);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, org.mockito.Mockito.times(4)).save(captor.capture());
        assertThat(retryEvent.getStatus()).isEqualTo("RETRY");
        assertThat(retryEvent.getRetryCount()).isEqualTo(1);
        assertThat(retryEvent.getNextAttemptAt()).isEqualTo(Instant.parse("2026-05-29T00:01:00Z"));
        assertThat(exhaustedEvent.getStatus()).isEqualTo("FAILED");
        assertThat(exhaustedEvent.getRetryCount()).isEqualTo(5);
        assertThat(exhaustedEvent.getNextAttemptAt()).isNull();
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

    private PlatformTransactionManager testTransactionManager() {
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
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
    }
}
