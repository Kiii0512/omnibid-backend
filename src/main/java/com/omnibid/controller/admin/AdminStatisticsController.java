package com.omnibid.controller.admin;

import com.omnibid.common.ApiResponse;
import com.omnibid.dto.admin.AdminStatisticsResponse;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/statistics")
@RequiredArgsConstructor
public class AdminStatisticsController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminStatisticsResponse>> getStatistics(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        CurrentUser admin = CurrentUser.of(userDetails);
        AdminStatisticsResponse response = adminService.getStatistics(admin);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
