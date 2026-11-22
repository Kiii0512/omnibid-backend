package com.omnibid.dto.notification;

import com.omnibid.entity.Notification;
import com.omnibid.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private NotificationType type;
    private String title;
    private String content;
    private String refType;
    private Long refId;
    private Boolean isRead;
    private Instant createdAt;

    public static NotificationResponse from(Notification n) {
        if (n == null) {
            return null;
        }
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
}
