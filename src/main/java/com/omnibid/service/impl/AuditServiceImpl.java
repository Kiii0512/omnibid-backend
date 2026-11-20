package com.omnibid.service.impl;

import com.omnibid.entity.AuditLog;
import com.omnibid.repository.AuditLogRepository;
import com.omnibid.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public void log(Long adminId, String action, String targetType, Long targetId, String detail) {
        AuditLog auditLog = AuditLog.builder()
                .adminId(adminId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .detail(detail)
                .build();
        auditLogRepository.save(auditLog);
        log.info("Audit log recorded: adminId={} action={} targetType={} targetId={}", adminId, action, targetType, targetId);
    }
}
