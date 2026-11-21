package com.omnibid.controller.admin;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.common.SortWhitelist;
import com.omnibid.dto.admin.AdminBanUserRequest;
import com.omnibid.dto.admin.AdminBanUserResponse;
import com.omnibid.dto.admin.AdminUserResponse;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> getUsers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) UserRole role,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        SortWhitelist.validate(pageable, Set.of("createdAt", "id", "fullName", "email", "status"));
        CurrentUser admin = CurrentUser.of(userDetails);
        PageResponse<AdminUserResponse> response = adminService.getUsers(admin, keyword, status, role, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/ban")
    public ResponseEntity<ApiResponse<AdminBanUserResponse>> banUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) AdminBanUserRequest request) {

        CurrentUser admin = CurrentUser.of(userDetails);
        String reason = request != null ? request.reason() : null;
        AdminBanUserResponse response = adminService.banUser(admin, id, reason);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/unban")
    public ResponseEntity<ApiResponse<AdminUserResponse>> unbanUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) AdminBanUserRequest request) {

        CurrentUser admin = CurrentUser.of(userDetails);
        String reason = request != null ? request.reason() : null;
        AdminUserResponse response = adminService.unbanUser(admin, id, reason);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
