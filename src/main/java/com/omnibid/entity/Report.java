package com.omnibid.entity;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "reported_user_id")
    private Long reportedUserId;

    @Column(name = "reported_product_id")
    private Long reportedProductId;

    @Column(name = "reported_order_id")
    private Long reportedOrderId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public com.omnibid.enums.ReportTargetType getTargetType() {
        if (reportedProductId != null) return com.omnibid.enums.ReportTargetType.PRODUCT;
        if (reportedUserId != null) return com.omnibid.enums.ReportTargetType.USER;
        return com.omnibid.enums.ReportTargetType.ORDER;
    }

    public String getDecisionNote() {
        return adminNote;
    }

    public void resolve(String note, Long adminId, Instant now) {
        require(status == ReportStatus.PENDING, "xử lý báo cáo");
        this.status = ReportStatus.RESOLVED;
        this.adminNote = note;
        this.resolvedBy = adminId;
        this.resolvedAt = now;
    }

    public void reject(String note, Long adminId, Instant now) {
        require(status == ReportStatus.PENDING, "bác bỏ báo cáo");
        this.status = ReportStatus.REJECTED;
        this.adminNote = note;
        this.resolvedBy = adminId;
        this.resolvedAt = now;
    }

    private void require(boolean condition, String operation) {
        if (!condition) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Không thể " + operation + " khi báo cáo ở trạng thái " + status
            );
        }
    }
}
