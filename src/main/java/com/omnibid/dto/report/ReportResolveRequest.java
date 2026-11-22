package com.omnibid.dto.report;

import com.omnibid.enums.ReportDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportResolveRequest(
        @NotNull(message = "Quyết định xử lý không được để trống")
        ReportDecision decision,

        @NotBlank(message = "Ghi chú xử lý không được để trống")
        @Size(max = 2000, message = "Ghi chú xử lý tối đa 2000 ký tự")
        String note
) {}
