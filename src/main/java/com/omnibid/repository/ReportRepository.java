package com.omnibid.repository;

import com.omnibid.entity.Report;
import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.ReportType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Report r WHERE r.id = :id")
    Optional<Report> findByIdForUpdate(@Param("id") Long id);

    List<Report> findByReporterId(Long reporterId);

    List<Report> findByStatus(ReportStatus status);

    List<Report> findByOrderId(Long orderId);

    boolean existsByOrderIdAndReportTypeAndStatus(Long orderId, ReportType reportType, ReportStatus status);

    boolean existsByReporterIdAndOrderIdAndStatus(Long reporterId, Long orderId, ReportStatus status);

    boolean existsByReporterIdAndReportedUserIdAndStatus(Long reporterId, Long reportedUserId, ReportStatus status);

    boolean existsByReporterIdAndReportedProductIdAndStatus(Long reporterId, Long reportedProductId, ReportStatus status);

    @Query("SELECT r FROM Report r WHERE (:status IS NULL OR r.status = :status) AND (:reportType IS NULL OR r.reportType = :reportType)")
    Page<Report> findAdminReports(@Param("status") ReportStatus status, @Param("reportType") ReportType reportType, Pageable pageable);
}
