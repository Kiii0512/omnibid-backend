package com.omnibid.dto.report;

import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.ReportTargetType;

import java.time.Instant;

public record ReportResponse(
        Long id,
        Long reporterId,
        ReportTargetType targetType,
        Long reportedProductId,
        Long reportedUserId,
        Long reportedOrderId,
        String reason,
        ReportStatus status,
        Instant createdAt,
        Instant resolvedAt,
        Long resolvedBy,
        String decisionNote
) {}
