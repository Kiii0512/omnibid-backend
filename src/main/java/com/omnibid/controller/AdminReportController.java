package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.report.AdminReportDetailResponse;
import com.omnibid.dto.report.AdminReportItemResponse;
import com.omnibid.dto.report.ReportResolveRequest;
import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.ReportTargetType;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping
    public ApiResponse<PageResponse<AdminReportItemResponse>> getAdminReports(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportTargetType targetType,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        CurrentUser admin = CurrentUser.of(userDetails);
        return ApiResponse.ok(reportService.getAdminReports(admin, status, targetType, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminReportDetailResponse> getAdminReportById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        CurrentUser admin = CurrentUser.of(userDetails);
        return ApiResponse.ok(reportService.getAdminReportById(admin, id));
    }

    @PostMapping("/{id}/resolve")
    public ApiResponse<AdminReportDetailResponse> resolveReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ReportResolveRequest request
    ) {
        CurrentUser admin = CurrentUser.of(userDetails);
        return ApiResponse.ok(reportService.resolveReport(admin, id, request));
    }
}
