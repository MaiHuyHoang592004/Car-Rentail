package com.rentflow.integration.outbox;

import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.outbox.entity.OutboxEvent;
import com.rentflow.outbox.repository.OutboxEventRepository;
import com.rentflow.outbox.service.OutboxEventDispatcher;
import com.rentflow.outbox.service.OutboxPublisherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("integration")
class OutboxPublisherIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisherService outboxPublisherService;

    @MockBean
    private OutboxEventDispatcher outboxEventDispatcher;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void processBatch_updatesRetryProgressionAndMarksFailedAtMaxAttempts() {
        OutboxEvent retryCandidate = saveEvent("PENDING", 0, null);
        OutboxEvent maxAttemptCandidate = saveEvent("RETRY", 2, Instant.parse("2026-05-29T00:00:00Z"));
        doThrow(new RuntimeException("dispatcher down")).when(outboxEventDispatcher).dispatch(any(OutboxEvent.class));

        outboxPublisherService.processBatch(10, 3, 60);

        OutboxEvent retried = outboxEventRepository.findById(retryCandidate.getId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo("RETRY");
        assertThat(retried.getRetryCount()).isEqualTo(1);
        assertThat(retried.getNextAttemptAt()).isEqualTo(Instant.parse("2026-05-29T00:01:00Z"));
        assertThat(retried.getLastError()).isEqualTo("dispatcher down");

        OutboxEvent failed = outboxEventRepository.findById(maxAttemptCandidate.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getRetryCount()).isEqualTo(3);
        assertThat(failed.getNextAttemptAt()).isNull();
        assertThat(failed.getLastError()).isEqualTo("dispatcher down");
    }

    @Test
    void processBatch_isIdempotentForAlreadySentEvents() {
        OutboxEvent candidate = saveEvent("PENDING", 0, null);

        outboxPublisherService.processBatch(10, 5, 60);
        outboxPublisherService.processBatch(10, 5, 60);

        verify(outboxEventDispatcher, times(1)).dispatch(any(OutboxEvent.class));
        OutboxEvent sent = outboxEventRepository.findById(candidate.getId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo("SENT");
        assertThat(sent.getRetryCount()).isEqualTo(0);
        assertThat(sent.getSentAt()).isEqualTo(Instant.parse("2026-05-29T00:00:00Z"));
    }

    private OutboxEvent saveEvent(String status, int retryCount, Instant nextAttemptAt) {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateType("BOOKING");
        event.setAggregateId(UUID.randomUUID());
        event.setEventType("BOOKING_CONFIRMED");
        event.setPayload("{\"bookingId\":\"test\"}");
        event.setStatus(status);
        event.setRetryCount(retryCount);
        event.setNextAttemptAt(nextAttemptAt);
        return outboxEventRepository.save(event);
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-05-29T00:00:00Z"), ZoneOffset.UTC);
        }
    }
}
