package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.report.*;
import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.ReportTargetType;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/api/v1/reports")
    public ResponseEntity<ApiResponse<ReportResponse>> createReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ReportRequest request) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        ReportResponse response = reportService.createReport(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/admin/reports")
    public ResponseEntity<ApiResponse<PageResponse<ReportDetailResponse>>> getAdminReports(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        PageResponse<ReportDetailResponse> response = reportService.getAdminReports(currentUser, status, targetType, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/admin/reports/{id}")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> getAdminReportById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        ReportDetailResponse response = reportService.getReportById(currentUser, id);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/api/v1/admin/reports/{id}/resolve")
    public ResponseEntity<ApiResponse<ReportResolveResponse>> resolveReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ReportResolveRequest request) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        ReportResolveResponse response = reportService.resolveReport(currentUser, id, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
