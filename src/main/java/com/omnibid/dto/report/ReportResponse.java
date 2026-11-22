package com.omnibid.dto.report;

import com.omnibid.enums.ReportStatus;

import java.time.Instant;

public record ReportResponse(
        Long id,
        Long reportedOrderId,
        Long reportedUserId,
        Long reportedProductId,
        String reason,
        ReportStatus status,
        Instant createdAt
) {}
