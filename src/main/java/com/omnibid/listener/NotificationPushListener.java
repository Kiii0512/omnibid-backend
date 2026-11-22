package com.omnibid.listener;

import com.omnibid.entity.Notification;
import com.omnibid.entity.User;
import com.omnibid.enums.UserRole;
import com.omnibid.event.NotificationRequested;
import com.omnibid.repository.NotificationRepository;
import com.omnibid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushListener {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNotificationRequested(NotificationRequested event) {
        if (event == null || event.userId() == null) {
            return;
        }
        try {
            User recipient = userRepository.findById(event.userId()).orElse(null);
            if (recipient == null) {
                log.warn("Bỏ qua thông báo vì không tìm thấy người nhận userId={}", event.userId());
                return;
            }
            if (recipient.getRole() == UserRole.ADMIN) {
                log.debug("Bỏ qua thông báo cho tài khoản ADMIN userId={} type={}", event.userId(), event.type());
                return;
            }

            Notification notification = Notification.builder()
                    .userId(event.userId())
                    .type(event.type())
                    .title(event.title())
                    .content(event.content())
                    .refType(event.refType())
                    .refId(event.refId())
                    .isRead(false)
                    .build();

            Notification saved = notificationRepository.save(notification);
            log.info("Saved notification id={} type={} for user={}", saved.getId(), event.type(), event.userId());
        } catch (Exception ex) {
            log.warn("Lưu thông báo sau COMMIT thất bại cho userId={} type={}: {}",
                    event.userId(), event.type(), ex.getMessage(), ex);
        }
    }
}
