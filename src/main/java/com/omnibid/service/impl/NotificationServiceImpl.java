package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.notification.NotificationItem;
import com.omnibid.dto.notification.NotificationResponse;
import com.omnibid.entity.Notification;
import com.omnibid.enums.NotificationType;
import com.omnibid.event.NotificationCreated;
import com.omnibid.repository.NotificationRepository;
import com.omnibid.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notify(Long userId, NotificationType type, String title, String content, String refType, Long refId) {
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
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyBatch(List<NotificationItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        // Khử trùng lặp theo (userId, type, refId) theo NTF-06 / UT-NTF-06
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
        Page<Notification> page;
        if (isRead != null) {
            page = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, isRead, pageable);
        } else {
            page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return PageResponse.from(page, NotificationResponse::from);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Notification not found with id: " + notificationId, HttpStatus.NOT_FOUND));

        if (!notification.getUserId().equals(userId)) {
            throw new AppException("NOT_FOUND", "Notification not found with id: " + notificationId, HttpStatus.NOT_FOUND);
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
        notificationRepository.markAllAsReadByUserId(userId);
    }
}
