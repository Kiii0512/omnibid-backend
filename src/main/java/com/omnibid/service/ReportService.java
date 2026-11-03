package com.omnibid.service;

import com.omnibid.common.PageResponse;
import com.omnibid.dto.report.ReportDetailResponse;
import com.omnibid.dto.report.ReportRequest;
import com.omnibid.dto.report.ReportResolveRequest;
import com.omnibid.dto.report.ReportResolveResponse;
import com.omnibid.dto.report.ReportResponse;
import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.ReportType;
import com.omnibid.security.CurrentUser;
import org.springframework.data.domain.Pageable;

public interface ReportService {

    ReportResponse createReport(CurrentUser currentUser, ReportRequest request);

    PageResponse<ReportDetailResponse> getAdminReports(CurrentUser admin, ReportStatus status, ReportType reportType, Pageable pageable);

    ReportResolveResponse resolveReport(CurrentUser admin, Long reportId, ReportResolveRequest request);
}
