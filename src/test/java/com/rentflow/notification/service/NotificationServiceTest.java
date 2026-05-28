package com.rentflow.notification.service;

import com.rentflow.notification.dto.NotificationResponse;
import com.rentflow.notification.entity.Notification;
import com.rentflow.notification.entity.NotificationDeliveryStatus;
import com.rentflow.notification.entity.NotificationType;
import com.rentflow.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
    }

    @Test
    void createSetsSentDeliveryStatusAndReturnsResponse() {
        UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(UUID.fromString("22222222-2222-4222-8222-222222222222"));
            notification.setCreatedAt(Instant.parse("2026-05-28T00:00:00Z"));
            return notification;
        });

        NotificationResponse response = notificationService.create(
                userId,
                NotificationType.DRIVER_VERIFICATION_EXPIRED,
                "Driver License Expired",
                "Your driver license has expired. Please submit a new verification.");

        assertThat(response.id()).isEqualTo(UUID.fromString("22222222-2222-4222-8222-222222222222"));
        assertThat(response.type()).isEqualTo("DRIVER_VERIFICATION_EXPIRED");
        assertThat(response.title()).isEqualTo("Driver License Expired");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
    }

    @Test
    void listMyNotificationsMapsPageInCreatedAtOrder() {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa");
        Notification newest = notification(userId, "New", Instant.parse("2026-05-28T00:00:00Z"));
        Notification older = notification(userId, "Old", Instant.parse("2026-05-27T00:00:00Z"));
        PageRequest pageable = PageRequest.of(0, 20);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(newest, older), pageable, 2));

        var page = notificationService.listMyNotifications(userId, pageable);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(NotificationResponse::title)
                .containsExactly("New", "Old");
    }

    private Notification notification(UUID userId, String title, Instant createdAt) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId(userId);
        notification.setType(NotificationType.DRIVER_VERIFICATION_EXPIRED);
        notification.setTitle(title);
        notification.setMessage("msg");
        notification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
        notification.setCreatedAt(createdAt);
        return notification;
    }
}
