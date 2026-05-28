package com.rentflow.outbox.service;

import com.rentflow.outbox.entity.OutboxEvent;

public interface OutboxEventDispatcher {

    void dispatch(OutboxEvent event);
}
