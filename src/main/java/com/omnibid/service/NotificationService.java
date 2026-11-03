package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.notification.NotificationItem;
import com.omnibid.dto.notification.NotificationResponse;
import com.omnibid.enums.NotificationType;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificationService {

    void notify(Long userId, NotificationType type, String title, String content, String refType, Long refId);

    void notifyBatch(List<NotificationItem> items);

    PageResponse<NotificationResponse> getNotifications(Long userId, Boolean isRead, Pageable pageable);

    NotificationResponse markAsRead(Long userId, Long notificationId);

    long getUnreadCount(Long userId);

    void markAllAsRead(Long userId);
}
