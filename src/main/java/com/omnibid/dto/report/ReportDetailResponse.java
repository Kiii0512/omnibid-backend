package com.omnibid.dto.report;

import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ReportAction;
import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.ReportType;

import java.time.LocalDateTime;

public record ReportDetailResponse(
        Long id,
        ReportType reportType,
        ReporterSummary reporter,
        ReportedUserSummary reportedUser,
        ReportedProductSummary reportedProduct,
        ReportOrderSummary order,
        String reason,
        ReportStatus status,
        ReportAction action,
        String adminNote,
        Long resolvedBy,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt
) {
    public record ReporterSummary(Long id, String fullName) {}
    public record ReportedUserSummary(Long id, String fullName, String status) {}
    public record ReportedProductSummary(Long id, String title, String status, Long activeAuctionId) {}
    public record ReportOrderSummary(Long id, OrderStatus status, String shipmentStatus, String paymentStatus) {}
}
