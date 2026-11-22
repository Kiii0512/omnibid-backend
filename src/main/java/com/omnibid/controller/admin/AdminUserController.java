package com.omnibid.controller.admin;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.admin.AdminBanUserResponse;
import com.omnibid.dto.admin.AdminReasonRequest;
import com.omnibid.dto.admin.AdminUnbanUserResponse;
import com.omnibid.dto.admin.AdminUserResponse;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminService adminService;

    @GetMapping
    public ApiResponse<PageResponse<AdminUserResponse>> getUsers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) UserRole role,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(adminService.getUsers(CurrentUser.of(userDetails), keyword, status, role, pageable));
    }

    @PostMapping("/{id}/ban")
    public ApiResponse<AdminBanUserResponse> banUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminReasonRequest request
    ) {
        String reason = request != null ? request.reason() : null;
        return ApiResponse.ok(adminService.banUser(CurrentUser.of(userDetails), id, reason));
    }

    @PostMapping("/{id}/unban")
    public ApiResponse<AdminUnbanUserResponse> unbanUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminReasonRequest request
    ) {
        String reason = request != null ? request.reason() : null;
        return ApiResponse.ok(adminService.unbanUser(CurrentUser.of(userDetails), id, reason));
    }
}
