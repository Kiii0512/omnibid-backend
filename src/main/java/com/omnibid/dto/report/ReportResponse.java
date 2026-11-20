package com.omnibid.dto.report;

import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.ReportType;

import java.time.LocalDateTime;

public record ReportResponse(
        Long id,
        ReportType reportType,
        Long orderId,
        Long reportedUserId,
        Long reportedProductId,
        String reason,
        ReportStatus status,
        LocalDateTime createdAt
) {}
