package com.rentflow.notification.entity;

public enum NotificationType {
    DRIVER_VERIFICATION_EXPIRED,
    PAYMENT_VOID_RETRY_REQUIRED,
    PAYMENT_VOID_RETRY_RESOLVED,
    PAYMENT_VOID_RETRY_FAILED_MAX_ATTEMPTS
}
