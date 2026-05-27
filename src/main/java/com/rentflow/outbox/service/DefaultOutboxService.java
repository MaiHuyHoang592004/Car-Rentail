package com.rentflow.outbox.service;

import com.rentflow.outbox.entity.OutboxEvent;
import com.rentflow.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DefaultOutboxService implements OutboxService {

    private final OutboxEventRepository repository;

    public DefaultOutboxService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(String aggregateType, UUID aggregateId, String eventType, String payloadJson) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payloadJson);
        event.setStatus("PENDING");
        repository.save(event);
    }
}
