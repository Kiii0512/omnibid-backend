package com.omnibid.dto.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        Long reportedProductId,
        Long reportedUserId,
        Long reportedOrderId,

        @NotBlank(message = "reason is required")
        @Size(min = 10, max = 2000, message = "reason must be between 10 and 2000 characters")
        String reason
) {
    public Long orderId() {
        return reportedOrderId;
    }
}
