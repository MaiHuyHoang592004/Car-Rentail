package com.rentflow.common.idempotency.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyFailureMarker {

    private final IdempotencyService idempotencyService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID idempotencyKeyId) {
        idempotencyService.fail(idempotencyKeyId);
    }
}
