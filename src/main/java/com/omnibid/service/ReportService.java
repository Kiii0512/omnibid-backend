package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.report.AdminReportDetailResponse;
import com.omnibid.dto.report.AdminReportItemResponse;
import com.omnibid.dto.report.ReportRequest;
import com.omnibid.dto.report.ReportResolveRequest;
import com.omnibid.dto.report.ReportResponse;
import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.ReportTargetType;
import com.omnibid.security.CurrentUser;
import org.springframework.data.domain.Pageable;

public interface ReportService {

    ReportResponse createReport(CurrentUser currentUser, ReportRequest request);

    PageResponse<AdminReportItemResponse> getAdminReports(CurrentUser admin, ReportStatus status, ReportTargetType targetType, Pageable pageable);

    AdminReportDetailResponse getAdminReportById(CurrentUser admin, Long reportId);

    AdminReportDetailResponse resolveReport(CurrentUser admin, Long reportId, ReportResolveRequest request);
}
