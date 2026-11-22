package com.omnibid.dto.report;

import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ReportStatus;

import java.time.Instant;

public record ReportDetailResponse(
        Long id,
        ReporterSummary reporter,
        ReportedUserSummary reportedUser,
        ReportedProductSummary reportedProduct,
        ReportOrderSummary reportedOrder,
        String reason,
        ReportStatus status,
        String adminNote,
        Long resolvedBy,
        Instant resolvedAt,
        Instant createdAt
) {
    public record ReporterSummary(Long id, String fullName) {}
    public record ReportedUserSummary(Long id, String fullName, String status) {}
    public record ReportedProductSummary(Long id, String title, String status, Long activeAuctionId) {}
    public record ReportOrderSummary(Long id, OrderStatus status, String paymentStatus) {}
}
