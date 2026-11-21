package com.omnibid.repository;

import com.omnibid.entity.Report;
import com.omnibid.enums.ReportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Report r WHERE r.id = :id")
    Optional<Report> findByIdForUpdate(@Param("id") Long id);

    List<Report> findByReporterId(Long reporterId);

    List<Report> findByStatus(ReportStatus status);

    List<Report> findByReportedOrderId(Long reportedOrderId);

    boolean existsByReporterIdAndReportedOrderIdAndStatus(Long reporterId, Long reportedOrderId, ReportStatus status);

    boolean existsByReporterIdAndReportedUserIdAndStatus(Long reporterId, Long reportedUserId, ReportStatus status);

    boolean existsByReporterIdAndReportedProductIdAndStatus(Long reporterId, Long reportedProductId, ReportStatus status);

    boolean existsByReportedOrderIdAndStatus(Long reportedOrderId, ReportStatus status);

    @Query("SELECT r FROM Report r WHERE (:status IS NULL OR r.status = :status) " +
           "AND (:targetType IS NULL OR " +
           "     (:targetType = com.omnibid.enums.ReportTargetType.PRODUCT AND r.reportedProductId IS NOT NULL) OR " +
           "     (:targetType = com.omnibid.enums.ReportTargetType.USER AND r.reportedUserId IS NOT NULL) OR " +
           "     (:targetType = com.omnibid.enums.ReportTargetType.ORDER AND r.reportedOrderId IS NOT NULL))")
    Page<Report> findAdminReports(@Param("status") ReportStatus status,
                                  @Param("targetType") com.omnibid.enums.ReportTargetType targetType,
                                  Pageable pageable);

    long countByStatus(ReportStatus status);
}
