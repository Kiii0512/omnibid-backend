package com.omnibid.listener;

import com.omnibid.dto.notification.NotificationResponse;
import com.omnibid.entity.Notification;
import com.omnibid.event.NotificationCreated;
import com.omnibid.repository.NotificationRepository;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushListener {

    private final WebSocketPublisher webSocketPublisher;
    private final NotificationRepository notificationRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreated event) {
        try {
            Notification notification = notificationRepository.findById(event.id()).orElse(null);
            if (notification == null) {
                log.warn("Notification not found for push: id={}", event.id());
                return;
            }
            long unreadCount = notificationRepository.countByUserIdAndIsReadFalse(event.userId());
            NotificationResponse payload = NotificationResponse.from(notification, unreadCount);
            webSocketPublisher.publishToUser(event.userId(), payload);
        } catch (Exception ex) {
            log.warn("Push failed for notification {}: {}", event.id(), ex.getMessage(), ex);
        }
    }
}
