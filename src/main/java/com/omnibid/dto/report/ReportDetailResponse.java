package com.omnibid.dto.report;

import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.ReportTargetType;

import java.time.Instant;

public record ReportDetailResponse(
        Long id,
        ReportTargetType targetType,
        ReporterSummary reporter,
        ReportedUserSummary reportedUser,
        ReportedProductSummary reportedProduct,
        ReportOrderSummary order,
        String reason,
        ReportStatus status,
        String decisionNote,
        Long resolvedBy,
        Instant resolvedAt,
        Instant createdAt
) {
    public record ReporterSummary(Long id, String fullName) {}
    public record ReportedUserSummary(Long id, String fullName, String status) {}
    public record ReportedProductSummary(Long id, String title, String status, Long activeAuctionId) {}
    public record ReportOrderSummary(Long id, String status) {}
}
