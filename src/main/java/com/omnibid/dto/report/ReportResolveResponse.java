package com.omnibid.dto.report;

import com.omnibid.enums.ReportAction;
import com.omnibid.enums.ReportStatus;

import java.time.LocalDateTime;
import java.util.Map;

public record ReportResolveResponse(
        Long id,
        ReportStatus status,
        ReportAction action,
        Long resolvedBy,
        LocalDateTime resolvedAt,
        Map<String, Object> effects
) {}
