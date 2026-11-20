package com.omnibid.dto.admin;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long adminId,
        String adminName,
        String action,
        String targetType,
        Long targetId,
        String detail,
        LocalDateTime createdAt
) {}
