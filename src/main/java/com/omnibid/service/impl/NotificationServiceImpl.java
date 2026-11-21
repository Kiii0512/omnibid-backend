package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.notification.NotificationItem;
import com.omnibid.dto.notification.NotificationResponse;
import com.omnibid.entity.Notification;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.Role;
import com.omnibid.event.NotificationCreated;
import com.omnibid.repository.NotificationRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(Long userId, NotificationType type, String title, String content, String refType, Long refId) {
        if (userId == null) {
            return;
        }

        if (userRepository != null) {
            boolean isAdmin = userRepository.findById(userId)
                    .map(u -> u.getRole() == Role.ADMIN)
                    .orElse(false);
            if (isAdmin) {
                log.debug("Skipping notification for admin user {}", userId);
                return;
            }
        }

        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .content(content)
                .refType(refType)
                .refId(refId)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        eventPublisher.publishEvent(new NotificationCreated(saved.getId(), userId));
        log.info("Saved notification id={} type={} for user={}", saved.getId(), type, userId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyBatch(List<NotificationItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Map<String, NotificationItem> uniqueMap = new LinkedHashMap<>();
        for (NotificationItem item : items) {
            if (item.getUserId() == null) continue;
            String key = item.getUserId() + ":" + item.getType() + ":" + item.getRefId();
            uniqueMap.putIfAbsent(key, item);
        }

        for (NotificationItem item : uniqueMap.values()) {
            notify(item.getUserId(), item.getType(), item.getTitle(), item.getContent(), item.getRefType(), item.getRefId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getNotifications(Long userId, Boolean isRead, Pageable pageable) {
        return getNotifications(userId, isRead, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getNotifications(Long userId, Boolean isRead, NotificationType type, Pageable pageable) {
        Page<Notification> page = notificationRepository.findNotifications(userId, isRead, type, pageable);
        return PageResponse.from(page, NotificationResponse::from);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy thông báo với id: " + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền đánh dấu thông báo này");
        }

        if (Boolean.TRUE.equals(notification.getIsRead())) {
            return NotificationResponse.from(notification);
        }

        notification.setIsRead(true);
        Notification saved = notificationRepository.save(notification);
        return NotificationResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }
}
