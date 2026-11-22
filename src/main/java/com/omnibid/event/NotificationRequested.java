package com.omnibid.event;

import com.omnibid.enums.NotificationType;

public record NotificationRequested(
        Long userId,
        NotificationType type,
        String title,
        String content,
        String refType,
        Long refId
) {
}
