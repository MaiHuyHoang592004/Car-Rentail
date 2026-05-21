package com.rentflow.common.exception;

import java.time.Duration;

public class RateLimitExceededException extends RentFlowException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("RATE_LIMIT_EXCEEDED", "Too many requests. Please retry later.");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
