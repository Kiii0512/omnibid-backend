package com.omnibid.dto.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.omnibid.entity.Notification;
import com.omnibid.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationResponse {

    private Long id;
    private NotificationType type;
    private String title;
    private String content;
    private String refType;
    private Long refId;
    private Boolean isRead;
    private LocalDateTime createdAt;
    private Long unreadCount;

    public static NotificationResponse from(Notification n) {
        if (n == null) return null;
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .content(n.getContent())
                .refType(n.getRefType())
                .refId(n.getRefId())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

    public static NotificationResponse from(Notification n, Long unreadCount) {
        if (n == null) return null;
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .content(n.getContent())
                .refType(n.getRefType())
                .refId(n.getRefId())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .unreadCount(unreadCount)
                .build();
    }
}
