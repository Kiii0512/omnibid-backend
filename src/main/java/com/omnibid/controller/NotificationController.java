package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.AppException;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.notification.NotificationResponse;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validateAuth(userDetails);
        PageResponse<NotificationResponse> result = notificationService.getNotifications(
                userDetails.getId(), isRead, PageRequest.of(page, Math.min(size, 100))
        );
        return ApiResponse.success(result);
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        validateAuth(userDetails);
        NotificationResponse response = notificationService.markAsRead(userDetails.getId(), id);
        return ApiResponse.success(response);
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateAuth(userDetails);
        long count = notificationService.getUnreadCount(userDetails.getId());
        return ApiResponse.success(Map.of("unreadCount", count));
    }

    @PatchMapping("/read-all")
    public ApiResponse<Map<String, String>> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        validateAuth(userDetails);
        notificationService.markAllAsRead(userDetails.getId());
        return ApiResponse.success(Map.of("message", "All notifications marked as read"));
    }

    private void validateAuth(CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new AppException("UNAUTHORIZED", "User not authenticated", HttpStatus.UNAUTHORIZED);
        }
    }
}
