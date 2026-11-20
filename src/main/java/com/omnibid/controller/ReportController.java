package com.omnibid.controller;

import com.omnibid.common.ApiResponse;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.report.ReportDetailResponse;
import com.omnibid.dto.report.ReportRequest;
import com.omnibid.dto.report.ReportResolveRequest;
import com.omnibid.dto.report.ReportResolveResponse;
import com.omnibid.dto.report.ReportResponse;
import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.ReportType;
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
            @RequestParam(required = false, defaultValue = "PENDING") ReportStatus status,
            @RequestParam(required = false) ReportType reportType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        PageResponse<ReportDetailResponse> response = reportService.getAdminReports(currentUser, status, reportType, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/api/v1/admin/reports/{id}/resolve")
    public ResponseEntity<ApiResponse<ReportResolveResponse>> resolveReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ReportResolveRequest request) {

        CurrentUser currentUser = CurrentUser.of(userDetails);
        ReportResolveResponse response = reportService.resolveReport(currentUser, id, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
