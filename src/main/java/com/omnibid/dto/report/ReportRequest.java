package com.omnibid.dto.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        Long reportedProductId,
        Long reportedUserId,
        Long reportedOrderId,

        @NotBlank(message = "Lý do báo cáo không được để trống")
        @Size(max = 1000, message = "Lý do báo cáo tối đa 1000 ký tự")
        String reason
) {}
