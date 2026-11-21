package com.omnibid.dto.report;

import com.omnibid.enums.ReportDecision;
import com.omnibid.enums.ReportStatus;

import java.time.Instant;

public record ReportResolveResponse(
        Long id,
        ReportStatus status,
        ReportDecision decision,
        String note,
        Long resolvedBy,
        Instant resolvedAt
) {}
