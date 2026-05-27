package com.rentflow.outbox.service;

import java.util.UUID;

public interface OutboxService {

    void append(String aggregateType, UUID aggregateId, String eventType, String payloadJson);
}
