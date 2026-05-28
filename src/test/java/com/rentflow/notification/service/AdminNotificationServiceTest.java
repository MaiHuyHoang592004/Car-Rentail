package com.rentflow.notification.service;

import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.notification.entity.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminNotificationServiceTest {

    @Mock private AuthUserRepository authUserRepository;
    @Mock private NotificationService notificationService;

    private AdminNotificationService adminNotificationService;

    @BeforeEach
    void setUp() {
        adminNotificationService = new AdminNotificationService(authUserRepository, notificationService);
    }

    @Test
    void resolveActiveAdminUserIdsQueriesActiveAdminOnly() {
        UUID adminId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        when(authUserRepository.findUserIdsByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(adminId));

        List<UUID> result = adminNotificationService.resolveActiveAdminUserIds();

        assertThat(result).containsExactly(adminId);
        verify(authUserRepository).findUserIdsByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
    }

    @Test
    void notifyPaymentVoidRetryRequiredCreatesNotificationsForAllActiveAdmins() {
        UUID adminA = UUID.fromString("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa");
        UUID adminB = UUID.fromString("bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb");
        UUID bookingId = UUID.fromString("cccccccc-cccc-4ccc-cccc-cccccccccccc");
        UUID paymentId = UUID.fromString("dddddddd-dddd-4ddd-dddd-dddddddddddd");
        when(authUserRepository.findUserIdsByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(adminA, adminB));

        adminNotificationService.notifyPaymentVoidRetryRequired(bookingId, paymentId, 2);

        verify(notificationService).create(eq(adminA), eq(NotificationType.PAYMENT_VOID_RETRY_REQUIRED), any(), any());
        verify(notificationService).create(eq(adminB), eq(NotificationType.PAYMENT_VOID_RETRY_REQUIRED), any(), any());
    }

    @Test
    void noActiveAdminsSkipsNotificationWrites() {
        when(authUserRepository.findUserIdsByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of());

        adminNotificationService.notifyPaymentVoidRetryResolved(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                1);

        verify(notificationService, never()).create(any(), any(), any(), any());
    }
}
