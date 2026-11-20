package com.omnibid.dto.report;

import com.omnibid.enums.ReportAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportResolveRequest(
        @NotNull(message = "action is required")
        ReportAction action,

        @NotNull(message = "note is required")
        @Size(min = 5, message = "note must be at least 5 characters")
        String note
) {}
