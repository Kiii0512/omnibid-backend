package com.omnibid.dto.report;

import com.omnibid.dto.user.UserRef;
import com.omnibid.enums.ReportStatus;

import java.time.Instant;

public record AdminReportItemResponse(
        Long id,
        UserRef reporter,
        ReportTargetSummary targets,
        String reason,
        ReportStatus status,
        Instant createdAt
) {}
