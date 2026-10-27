package com.omnibid.repository;

import com.omnibid.entity.Report;
import com.omnibid.enums.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByReporterId(Long reporterId);
    List<Report> findByStatus(ReportStatus status);
    List<Report> findByOrderId(Long orderId);
}
