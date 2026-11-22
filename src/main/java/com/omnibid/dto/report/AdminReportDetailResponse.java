package com.omnibid.dto.report;

import com.omnibid.dto.user.UserRef;
import com.omnibid.enums.ReportStatus;

import java.time.Instant;

public record AdminReportDetailResponse(
        Long id,
        UserRef reporter,
        ReportTargetSummary targets,
        String reason,
        ReportStatus status,
        Instant createdAt,
        String adminNote,
        UserRef resolvedBy,
        Instant resolvedAt
) {}
