package com.omnibid.dto.report;

import com.omnibid.enums.ReportDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportResolveRequest(
        @NotNull(message = "decision is required")
        ReportDecision decision,

        @NotNull(message = "note is required")
        @Size(min = 5, message = "note must be at least 5 characters")
        String note
) {}
