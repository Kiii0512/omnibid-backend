package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.notification.NotificationResponse;
import com.omnibid.enums.NotificationType;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    void notify(Long userId, NotificationType type, String title, String content, String refType, Long refId);

    PageResponse<NotificationResponse> getNotifications(Long userId, Boolean isRead, NotificationType type, Pageable pageable);

    NotificationResponse markAsRead(Long userId, Long notificationId);
}
