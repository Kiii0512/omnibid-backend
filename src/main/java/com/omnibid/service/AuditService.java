package com.omnibid.service;

public interface AuditService {
    void log(Long adminId, String action, String targetType, Long targetId, String detail);
}
