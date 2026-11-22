package com.omnibid.service;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.MaskingUtils;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.notification.NotificationResponse;
import com.omnibid.entity.Notification;
import com.omnibid.entity.User;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.event.NotificationRequested;
import com.omnibid.listener.NotificationPushListener;
import com.omnibid.repository.NotificationRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NotificationServiceImpl notificationService;
    private NotificationPushListener pushListener;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(notificationRepository, eventPublisher);
        pushListener = new NotificationPushListener(notificationRepository, userRepository);
    }

    @Test
    @DisplayName("notify(...) chỉ phát sự kiện NotificationRequested, không ghi trực tiếp vào DB")
    void notify_PublishesNotificationRequested_WithoutDirectDbWrite() {
        Long userId = 2L;
        NotificationType type = NotificationType.OUTBID;
        String title = NotificationTemplates.outbidTitle();
        String content = NotificationTemplates.outbidContent(100L, BigDecimal.valueOf(520000));

        notificationService.notify(userId, type, title, content, "AUCTION", 100L);

        verifyNoInteractions(notificationRepository);
        ArgumentCaptor<NotificationRequested> captor = ArgumentCaptor.forClass(NotificationRequested.class);
        verify(eventPublisher).publishEvent(captor.capture());

        NotificationRequested event = captor.getValue();
        assertEquals(userId, event.userId());
        assertEquals(type, event.type());
        assertEquals(title, event.title());
        assertEquals(content, event.content());
        assertEquals("AUCTION", event.refType());
        assertEquals(100L, event.refId());
    }

    @Test
    @DisplayName("NotificationPushListener lưu thông báo cho USER sau COMMIT và bỏ qua ADMIN")
    void pushListener_SavesForUser_SkipsAdmin() {
        User normalUser = User.builder().id(2L).role(UserRole.USER).status(UserStatus.ACTIVE).build();
        User adminUser = User.builder().id(1L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(normalUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(55L);
            return n;
        });

        pushListener.onNotificationRequested(new NotificationRequested(
                2L, NotificationType.OUTBID, "Tiêu đề", "Nội dung", "AUCTION", 10L
        ));
        verify(notificationRepository, times(1)).save(any(Notification.class));

        pushListener.onNotificationRequested(new NotificationRequested(
                1L, NotificationType.REPORT_RESOLVED, "Tiêu đề", "Nội dung", "REPORT", 10L
        ));
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    @DisplayName("NotificationPushListener bắt ngoại lệ DB sau COMMIT mà không ném ra")
    void pushListener_DbError_DoesNotThrow() {
        User normalUser = User.builder().id(2L).role(UserRole.USER).status(UserStatus.ACTIVE).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(normalUser));
        when(notificationRepository.save(any(Notification.class))).thenThrow(new RuntimeException("DB error"));

        assertDoesNotThrow(() -> pushListener.onNotificationRequested(
                new NotificationRequested(2L, NotificationType.ORDER_UPDATE, "Tiêu đề", "Nội dung", "ORDER", 9L)
        ));
    }

    @Test
    @DisplayName("NotificationTemplates hỗ trợ đủ 5 loại, định dạng tiền 520.000 ₫ và +07:00")
    void notificationTemplates_FormatsCurrencyAndDateTime() {
        assertEquals("520.000 ₫", NotificationTemplates.formatCurrency(BigDecimal.valueOf(520000)));
        assertEquals("0 ₫", NotificationTemplates.formatCurrency(null));
        assertEquals("09:00 20/11/2026", NotificationTemplates.formatDateTime(Instant.parse("2026-11-20T02:00:00Z")));

        for (NotificationType type : NotificationType.values()) {
            NotificationTemplates.TemplateResult result = NotificationTemplates.forType(type);
            assertNotNull(result);
            assertFalse(result.title().isBlank());
            assertFalse(result.content().isBlank());
            assertFalse(result.content().toLowerCase().contains("reserve_price"));
        }

        assertEquals("N***n", MaskingUtils.maskName("Nguyen Van An"));
        assertEquals("***", MaskingUtils.maskName(null));
    }

    @Test
    @DisplayName("markAsRead: chủ thông báo đánh dấu đã đọc và gọi lặp idempotent")
    void markAsRead_Owner_IdempotentSuccess() {
        Long userId = 2L;
        Long notificationId = 10L;

        Notification unread = Notification.builder()
                .id(notificationId)
                .userId(userId)
                .type(NotificationType.AUCTION_RESULT)
                .title("Bạn đã thắng")
                .content("Nội dung")
                .refType("AUCTION")
                .refId(100L)
                .isRead(false)
                .createdAt(Instant.parse("2026-11-20T02:00:00Z"))
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(unread));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response1 = notificationService.markAsRead(userId, notificationId);
        assertTrue(response1.getIsRead());
        verify(notificationRepository, times(1)).save(any(Notification.class));

        NotificationResponse response2 = notificationService.markAsRead(userId, notificationId);
        assertTrue(response2.getIsRead());
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    @DisplayName("markAsRead: người khác nhận 403 FORBIDDEN, không tồn tại nhận 404 NOT_FOUND")
    void markAsRead_OtherUserOrNotFound_ThrowsExpectedErrors() {
        Notification notification = Notification.builder()
                .id(10L)
                .userId(2L)
                .type(NotificationType.OUTBID)
                .isRead(false)
                .build();

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));
        BusinessException forbidden = assertThrows(BusinessException.class,
                () -> notificationService.markAsRead(3L, 10L));
        assertEquals(ErrorCode.FORBIDDEN, forbidden.getErrorCode());

        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());
        BusinessException notFound = assertThrows(BusinessException.class,
                () -> notificationService.markAsRead(2L, 99L));
        assertEquals(ErrorCode.NOT_FOUND, notFound.getErrorCode());
    }

    @Test
    @DisplayName("getNotifications phân trang, lọc và từ chối sort ngoài whitelist")
    void getNotifications_FiltersAndValidatesSort() {
        Long userId = 2L;
        Notification n = Notification.builder()
                .id(1L)
                .userId(userId)
                .type(NotificationType.OUTBID)
                .title("Outbid")
                .content("Content")
                .isRead(false)
                .createdAt(Instant.parse("2026-11-20T02:00:00Z"))
                .build();

        PageImpl<Notification> page = new PageImpl<>(List.of(n), PageRequest.of(0, 10), 1);
        when(notificationRepository.findNotifications(eq(userId), eq(false), eq(NotificationType.OUTBID), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<NotificationResponse> result = notificationService.getNotifications(
                userId, false, NotificationType.OUTBID, PageRequest.of(0, 10)
        );
        assertEquals(1, result.getTotalElements());
        assertEquals(1L, result.getContent().get(0).getId());

        BusinessException invalidSort = assertThrows(BusinessException.class, () ->
                notificationService.getNotifications(
                        userId, null, null, PageRequest.of(0, 10, Sort.by("unknownField"))
                )
        );
        assertEquals(ErrorCode.VALIDATION_ERROR, invalidSort.getErrorCode());
    }
}
