package com.omnibid.dto.report;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        Long reportedUserId,

        Long reportedProductId,

        Long reportedOrderId,

        @NotNull(message = "reason is required")
        @Size(min = 10, max = 2000, message = "reason must be between 10 and 2000 characters")
        String reason
) {}
