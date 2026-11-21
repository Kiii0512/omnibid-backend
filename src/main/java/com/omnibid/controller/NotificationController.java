package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.common.SortWhitelist;
import com.omnibid.dto.notification.NotificationResponse;
import com.omnibid.enums.NotificationType;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) NotificationType type,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        SortWhitelist.validate(pageable, Set.of("createdAt", "isRead"));
        PageResponse<NotificationResponse> result = notificationService.getNotifications(
                userDetails.getId(), isRead, type, pageable
        );
        return ApiResponse.success(result);
    }

    @PostMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        NotificationResponse response = notificationService.markAsRead(userDetails.getId(), id);
        return ApiResponse.success(response);
    }
}
