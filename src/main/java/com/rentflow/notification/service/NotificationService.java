package com.rentflow.notification.service;

import com.rentflow.notification.dto.NotificationResponse;
import com.rentflow.notification.entity.Notification;
import com.rentflow.notification.entity.NotificationDeliveryStatus;
import com.rentflow.notification.entity.NotificationType;
import com.rentflow.notification.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public NotificationResponse create(UUID userId, NotificationType type, String title, String message) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
        notification = notificationRepository.save(notification);
        return NotificationResponse.from(notification);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listMyNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::from);
    }
}
