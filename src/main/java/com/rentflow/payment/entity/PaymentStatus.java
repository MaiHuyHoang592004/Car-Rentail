package com.rentflow.payment.entity;

public enum PaymentStatus {
    UNPAID,
    PENDING_TRANSFER,
    AUTHORIZED,
    CAPTURED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    VOIDED,
    FAILED,
    RECONCILIATION_REQUIRED
}
