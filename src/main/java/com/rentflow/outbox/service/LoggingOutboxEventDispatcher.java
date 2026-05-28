package com.rentflow.outbox.service;

import com.rentflow.outbox.entity.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingOutboxEventDispatcher implements OutboxEventDispatcher {

    @Override
    public void dispatch(OutboxEvent event) {
        // Placeholder publisher for Phase 9.6; can be replaced with Kafka/HTTP integration later.
        log.info("Outbox event dispatched: id={}, type={}, aggregateType={}",
                event.getId(), event.getEventType(), event.getAggregateType());
    }
}
