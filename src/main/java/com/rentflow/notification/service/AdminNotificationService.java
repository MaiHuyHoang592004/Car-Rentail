package com.rentflow.notification.service;

import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.notification.entity.NotificationType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminNotificationService {

    private final AuthUserRepository authUserRepository;
    private final NotificationService notificationService;

    public AdminNotificationService(
            AuthUserRepository authUserRepository,
            NotificationService notificationService) {
        this.authUserRepository = authUserRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public void notifyPaymentVoidRetryRequired(UUID bookingId, UUID paymentId, int retryCount) {
        String message = "Booking " + bookingId
                + " payment " + paymentId
                + " requires void retry (attempt " + retryCount + ").";
        notifyActiveAdmins(
                NotificationType.PAYMENT_VOID_RETRY_REQUIRED,
                "Payment Void Retry Required",
                message);
    }

    @Transactional
    public void notifyPaymentVoidRetryResolved(UUID bookingId, UUID paymentId, int retryCount) {
        String message = "Booking " + bookingId
                + " payment " + paymentId
                + " void retry resolved at attempt " + retryCount + ".";
        notifyActiveAdmins(
                NotificationType.PAYMENT_VOID_RETRY_RESOLVED,
                "Payment Void Retry Resolved",
                message);
    }

    @Transactional
    public void notifyPaymentVoidRetryFailedMaxAttempts(UUID bookingId, UUID paymentId, int retryCount) {
        String message = "Booking " + bookingId
                + " payment " + paymentId
                + " void retry reached max attempts (" + retryCount + ").";
        notifyActiveAdmins(
                NotificationType.PAYMENT_VOID_RETRY_FAILED_MAX_ATTEMPTS,
                "Payment Void Retry Failed",
                message);
    }

    @Transactional(readOnly = true)
    public List<UUID> resolveActiveAdminUserIds() {
        return authUserRepository.findUserIdsByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
    }

    private void notifyActiveAdmins(NotificationType type, String title, String message) {
        List<UUID> adminIds = resolveActiveAdminUserIds();
        for (UUID adminId : adminIds) {
            notificationService.create(adminId, type, title, message);
        }
    }
}
