package com.omnibid.repository;

import com.omnibid.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByAdminIdOrderByCreatedAtDesc(Long adminId);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:adminId IS NULL OR a.adminId = :adminId)
          AND (:action IS NULL OR a.action = :action)
          AND (:targetType IS NULL OR a.targetType = :targetType)
          AND (:targetId IS NULL OR a.targetId = :targetId)
          AND (:from IS NULL OR a.createdAt >= :from)
          AND (:to IS NULL OR a.createdAt <= :to)
    """)
    Page<AuditLog> findAdminAuditLogs(@Param("adminId") Long adminId,
                                      @Param("action") String action,
                                      @Param("targetType") String targetType,
                                      @Param("targetId") Long targetId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to,
                                      Pageable pageable);
}
