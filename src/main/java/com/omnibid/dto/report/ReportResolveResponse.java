package com.omnibid.dto.report;

import com.omnibid.enums.ReportStatus;

import java.time.Instant;

public record ReportResolveResponse(
        Long id,
        ReportStatus status,
        String adminNote,
        Long resolvedBy,
        Instant resolvedAt
) {}
