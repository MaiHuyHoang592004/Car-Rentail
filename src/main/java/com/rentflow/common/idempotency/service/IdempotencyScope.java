package com.rentflow.common.idempotency.service;

public enum IdempotencyScope {
    CREATE_BOOKING,
    CANCEL_BOOKING,
    AUTHORIZE_PAYMENT,
    CAPTURE_PAYMENT,
    VOID_PAYMENT,
    REFUND_PAYMENT,
    RECONCILE_PAYMENT
}
