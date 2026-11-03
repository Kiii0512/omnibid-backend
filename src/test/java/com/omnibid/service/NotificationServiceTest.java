package com.omnibid.service;

import com.omnibid.common.AppException;
import com.omnibid.common.MaskingUtils;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.notification.NotificationItem;
import com.omnibid.dto.notification.NotificationResponse;
import com.omnibid.entity.Notification;
import com.omnibid.enums.NotificationType;
import com.omnibid.event.NotificationCreated;
import com.omnibid.listener.NotificationPushListener;
import com.omnibid.repository.NotificationRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.messaging.MessagingException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private WebSocketPublisher webSocketPublisher;

    private NotificationServiceImpl notificationService;
    private NotificationPushListener pushListener;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(notificationRepository, eventPublisher);
        pushListener = new NotificationPushListener(webSocketPublisher, notificationRepository);
    }

    @Test
    @DisplayName("UT-NTF-01 notify(...) lưu 1 dòng đúng type, refType, refId và phát sự kiện NotificationCreated")
    void notify_SavesNotification_AndPublishesEvent() {
        Long userId = 2L;
        NotificationType type = NotificationType.OUTBID;
        String title = "Bạn đã bị vượt giá";
        String content = "Bạn vừa bị vượt giá tại phiên #100. Mức giá cao nhất hiện tại là 120,000đ.";
        String refType = "AUCTION";
        Long refId = 100L;

        Notification saved = Notification.builder()
                .id(1L)
                .userId(userId)
                .type(type)
                .title(title)
                .content(content)
                .refType(refType)
                .refId(refId)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        notificationService.notify(userId, type, title, content, refType, refId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification captured = captor.getValue();
        assertEquals(userId, captured.getUserId());
        assertEquals(type, captured.getType());
        assertEquals(title, captured.getTitle());
        assertEquals(content, captured.getContent());
        assertEquals(refType, captured.getRefType());
        assertEquals(refId, captured.getRefId());
        assertFalse(captured.getIsRead());

        ArgumentCaptor<NotificationCreated> eventCaptor = ArgumentCaptor.forClass(NotificationCreated.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationCreated capturedEvent = eventCaptor.getValue();
        assertEquals(1L, capturedEvent.id());
        assertEquals(userId, capturedEvent.userId());
    }

    @Test
    @DisplayName("UT-NTF-02 Mẫu nội dung: Mỗi NotificationType dựng được tiêu đề/nội dung; không chứa reserve_price, email, SĐT; tên người đặt giá dạng che")
    void notificationTemplates_BuildsValidContent_WithoutConfidentialInfo() {
        for (NotificationType type : NotificationType.values()) {
            NotificationTemplates.TemplateResult result = NotificationTemplates.forType(type);
            assertNotNull(result, "Template result should not be null for " + type);
            assertNotNull(result.title(), "Title should not be null for " + type);
            assertFalse(result.title().isBlank(), "Title should not be blank for " + type);
            assertNotNull(result.content(), "Content should not be null for " + type);
            assertFalse(result.content().isBlank(), "Content should not be blank for " + type);

            // Bảo mật NTF-07: Không lộ reserve_price, email, sđt
            assertFalse(result.content().toLowerCase().contains("reserve_price"));
            assertFalse(result.title().toLowerCase().contains("reserve_price"));
            assertFalse(result.content().matches(".*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*"));
            assertFalse(result.content().matches(".*(\\+84|0)[3|5|7|8|9][0-9]{8}.*"));
        }

        // Kiểm tra hàm che tên MaskingUtils
        assertEquals("N***A", MaskingUtils.maskName("Nguyen Van An"));
        assertEquals("T***", MaskingUtils.maskName("Tran"));
        assertEquals("***", MaskingUtils.maskName(null));
        assertEquals("***", MaskingUtils.maskName("   "));
    }

    @Test
    @DisplayName("UT-NTF-03 Đánh dấu đã đọc: Chủ thông báo isRead = true, gọi lặp không lỗi")
    void markAsRead_OwnerMarksRead_IdempotentSuccess() {
        Long userId = 2L;
        Long notificationId = 10L;

        Notification unread = Notification.builder()
                .id(notificationId)
                .userId(userId)
                .type(NotificationType.AUCTION_WON)
                .title("Bạn đã thắng")
                .content("Nội dung")
                .refType("AUCTION")
                .refId(100L)
                .isRead(false)
                .build();

        Notification read = Notification.builder()
                .id(notificationId)
                .userId(userId)
                .type(NotificationType.AUCTION_WON)
                .title("Bạn đã thắng")
                .content("Nội dung")
                .refType("AUCTION")
                .refId(100L)
                .isRead(true)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(unread));
        when(notificationRepository.save(any(Notification.class))).thenReturn(read);

        NotificationResponse response1 = notificationService.markAsRead(userId, notificationId);
        assertTrue(response1.getIsRead());
        verify(notificationRepository).save(any(Notification.class));

        // Gọi lặp lại khi đã đọc (idempotent, không ném lỗi)
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(read));
        NotificationResponse response2 = notificationService.markAsRead(userId, notificationId);
        assertTrue(response2.getIsRead());
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    @DisplayName("UT-NTF-04 Đánh dấu thông báo người khác bị 404 NOT_FOUND")
    void markAsRead_OtherUser_ThrowsNotFound() {
        Long ownerId = 2L;
        Long attackerId = 3L;
        Long notificationId = 10L;

        Notification notification = Notification.builder()
                .id(notificationId)
                .userId(ownerId)
                .type(NotificationType.AUCTION_WON)
                .title("Bạn đã thắng")
                .content("Nội dung")
                .refType("AUCTION")
                .refId(100L)
                .isRead(false)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        AppException ex = assertThrows(AppException.class, () -> notificationService.markAsRead(attackerId, notificationId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("NOT_FOUND", ex.getCode());
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("UT-NTF-05 Lỗi đẩy WebSocket: WebSocketPublisher ném MessagingException, Listener bắt log WARN và không ném ra")
    void pushListener_WebSocketFails_CatchesAndLogsWarning() {
        Long notificationId = 10L;
        Long userId = 2L;

        Notification notification = Notification.builder()
                .id(notificationId)
                .userId(userId)
                .type(NotificationType.PAYMENT_RESULT)
                .title("Thanh toán thành công")
                .content("Đơn #9 đã được thanh toán.")
                .refType("ORDER")
                .refId(9L)
                .isRead(false)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(notificationRepository.countByUserIdAndIsReadFalse(userId)).thenReturn(1L);

        doThrow(new MessagingException("Broker is unavailable"))
                .when(webSocketPublisher).publishToUser(eq(userId), any());

        assertDoesNotThrow(() -> pushListener.onNotificationCreated(new NotificationCreated(notificationId, userId)));

        verify(webSocketPublisher).publishToUser(eq(userId), any());
    }

    @Test
    @DisplayName("UT-NTF-06 Khử trùng lặp khi thao tác hàng loạt: 2 sự kiện cùng (userId, type, refId) chỉ ghi 1 thông báo")
    void notifyBatch_DeduplicatesByUserIdTypeAndRefId() {
        Long userId = 5L;
        NotificationItem item1 = NotificationItem.builder()
                .userId(userId)
                .type(NotificationType.AUCTION_ENDED)
                .title("Phiên đã bị hủy: Vi phạm")
                .content("Phiên #100 đã bị hủy.")
                .refType("AUCTION")
                .refId(100L)
                .build();

        NotificationItem item2 = NotificationItem.builder()
                .userId(userId)
                .type(NotificationType.AUCTION_ENDED)
                .title("Phiên đã bị hủy: Vi phạm")
                .content("Phiên #100 đã bị hủy.")
                .refType("AUCTION")
                .refId(100L)
                .build();

        Notification saved = Notification.builder()
                .id(20L)
                .userId(userId)
                .type(NotificationType.AUCTION_ENDED)
                .title("Phiên đã bị hủy: Vi phạm")
                .content("Phiên #100 đã bị hủy.")
                .refType("AUCTION")
                .refId(100L)
                .isRead(false)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        notificationService.notifyBatch(List.of(item1, item2));

        // Chỉ lưu và phát sự kiện 1 lần
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(eventPublisher, times(1)).publishEvent(any(NotificationCreated.class));
    }

    @Test
    @DisplayName("getNotifications phân trang và lọc theo isRead")
    void getNotifications_PaginationAndFilter() {
        Long userId = 2L;
        Notification n = Notification.builder()
                .id(1L)
                .userId(userId)
                .type(NotificationType.OUTBID)
                .title("Outbid")
                .content("Content")
                .isRead(false)
                .build();

        PageImpl<Notification> page = new PageImpl<>(List.of(n), PageRequest.of(0, 20), 1);
        when(notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(eq(userId), eq(false), any(PageRequest.class)))
                .thenReturn(page);

        PageResponse<NotificationResponse> result = notificationService.getNotifications(userId, false, PageRequest.of(0, 20));
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getContent().get(0).getId());
    }

    @Test
    @DisplayName("getUnreadCount trả về đúng số lượng chưa đọc")
    void getUnreadCount_ReturnsCount() {
        Long userId = 2L;
        when(notificationRepository.countByUserIdAndIsReadFalse(userId)).thenReturn(5L);

        long count = notificationService.getUnreadCount(userId);
        assertEquals(5L, count);
    }

    @Test
    @DisplayName("markAllAsRead cập nhật toàn bộ thông báo chưa đọc thành đã đọc")
    void markAllAsRead_UpdatesAll() {
        Long userId = 2L;
        when(notificationRepository.markAllAsReadByUserId(userId)).thenReturn(3);

        notificationService.markAllAsRead(userId);
        verify(notificationRepository).markAllAsReadByUserId(userId);
    }
}
