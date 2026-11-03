package com.omnibid.dto.notification;

import com.omnibid.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationItem {
    private Long userId;
    private NotificationType type;
    private String title;
    private String content;
    private String refType;
    private Long refId;
}
