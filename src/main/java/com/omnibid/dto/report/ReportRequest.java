package com.omnibid.dto.report;

import com.omnibid.enums.ReportType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        @NotNull(message = "reportType is required")
        ReportType reportType,

        Long reportedUserId,

        Long reportedProductId,

        Long orderId,

        @NotNull(message = "reason is required")
        @Size(min = 10, max = 2000, message = "reason must be between 10 and 2000 characters")
        String reason
) {}
