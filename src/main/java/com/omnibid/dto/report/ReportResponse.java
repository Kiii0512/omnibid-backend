package com.omnibid.dto.report;

import com.omnibid.enums.ReportStatus;

import java.time.Instant;

public record ReportResponse(
        Long id,
        ReportStatus status,
        Instant createdAt
) {}
