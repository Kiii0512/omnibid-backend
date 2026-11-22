package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.PageResponse;
import com.omnibid.common.SortWhitelist;
import com.omnibid.dto.notification.NotificationResponse;
import com.omnibid.entity.Notification;
import com.omnibid.enums.NotificationType;
import com.omnibid.event.NotificationRequested;
import com.omnibid.repository.NotificationRepository;
import com.omnibid.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt", "id");

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void notify(Long userId, NotificationType type, String title, String content, String refType, Long refId) {
        if (userId == null) {
            return;
        }
        eventPublisher.publishEvent(new NotificationRequested(userId, type, title, content, refType, refId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getNotifications(Long userId, Boolean isRead, NotificationType type, Pageable pageable) {
        SortWhitelist.validate(pageable, ALLOWED_SORT_PROPERTIES);
        Pageable effective = normalizePageable(pageable);
        Page<Notification> page = notificationRepository.findNotifications(userId, isRead, type, effective);
        return PageResponse.from(page, NotificationResponse::from);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy thông báo #" + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập thông báo này");
        }

        if (Boolean.TRUE.equals(notification.getIsRead())) {
            return NotificationResponse.from(notification);
        }

        notification.setIsRead(true);
        Notification saved = notificationRepository.save(notification);
        return NotificationResponse.from(saved);
    }

    private Pageable normalizePageable(Pageable pageable) {
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.min(Math.max(1, pageable.getPageSize()), 100);
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "createdAt");
        return PageRequest.of(page, size, sort);
    }
}
