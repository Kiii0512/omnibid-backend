package com.omnibid.service.impl;

import com.omnibid.common.AfterCommitExecutor;
import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.report.*;
import com.omnibid.entity.*;
import com.omnibid.enums.*;
import com.omnibid.repository.*;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.NotificationService;
import com.omnibid.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AuctionRepository auctionRepository;
    private final Clock clock;
    private final NotificationService notificationService;
    private final AfterCommitExecutor afterCommitExecutor;

    @Override
    @Transactional
    public ReportResponse createReport(CurrentUser currentUser, ReportRequest request) {
        if (request.reason() == null || request.reason().trim().length() < 10) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Lý do báo cáo phải có ít nhất 10 ký tự");
        }

        if (request.reportedUserId() == null && request.reportedProductId() == null && request.reportedOrderId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phải chọn ít nhất một đối tượng báo cáo (sản phẩm, người dùng hoặc đơn hàng)");
        }

        User reporter = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Người dùng không tồn tại"));
        if (reporter.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Tài khoản của bạn đã bị khóa");
        }

        if (request.reportedUserId() != null) {
            if (request.reportedUserId().equals(currentUser.id())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể tự báo cáo chính mình");
            }
            if (!userRepository.existsById(request.reportedUserId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Người dùng bị báo cáo không tồn tại");
            }
        }

        if (request.reportedProductId() != null) {
            Product product = productRepository.findById(request.reportedProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm bị báo cáo không tồn tại"));
            if (product.getSellerId().equals(currentUser.id())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể tự báo cáo sản phẩm của chính mình");
            }
        }

        if (request.reportedOrderId() != null) {
            Order order = orderRepository.findById(request.reportedOrderId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại"));
            if (!order.getBuyerId().equals(currentUser.id()) && !order.getSellerId().equals(currentUser.id())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ bên tham gia đơn hàng mới có quyền báo cáo");
            }
        }

        Report report = Report.builder()
                .reporterId(currentUser.id())
                .reportedUserId(request.reportedUserId())
                .reportedProductId(request.reportedProductId())
                .reportedOrderId(request.reportedOrderId())
                .reason(request.reason().trim())
                .status(ReportStatus.PENDING)
                .build();

        Report saved = reportRepository.save(report);

        return new ReportResponse(
                saved.getId(),
                saved.getReporterId(),
                saved.getTargetType(),
                saved.getReportedProductId(),
                saved.getReportedUserId(),
                saved.getReportedOrderId(),
                saved.getReason(),
                saved.getStatus(),
                saved.getCreatedAt(),
                saved.getResolvedAt(),
                saved.getResolvedBy(),
                saved.getDecisionNote()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportDetailResponse> getAdminReports(CurrentUser admin, ReportStatus status, ReportTargetType targetType, Pageable pageable) {
        requireAdmin(admin);

        Page<Report> page = reportRepository.findAdminReports(status, targetType, pageable);
        return PageResponse.from(page, this::mapToDetailResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportDetailResponse getReportById(CurrentUser admin, Long reportId) {
        requireAdmin(admin);

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Báo cáo không tồn tại"));

        return mapToDetailResponse(report);
    }

    @Override
    @Transactional
    public ReportResolveResponse resolveReport(CurrentUser admin, Long reportId, ReportResolveRequest request) {
        requireAdmin(admin);

        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Báo cáo không tồn tại"));

        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Báo cáo đã được xử lý trước đó");
        }

        if (request.note() == null || request.note().trim().length() < 5) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ghi chú xử lý phải có ít nhất 5 ký tự");
        }

        Instant now = Instant.now(clock);
        if (request.decision() == ReportDecision.RESOLVED) {
            report.resolve(request.note().trim(), admin.id(), now);
        } else {
            report.reject(request.note().trim(), admin.id(), now);
        }

        Report saved = reportRepository.save(report);
        log.info("Admin {} {} report {}: note={}", admin.id(), request.decision(), reportId, request.note());

        if (afterCommitExecutor != null && notificationService != null) {
            String decisionStr = request.decision() == ReportDecision.RESOLVED ? "Đã xử lý" : "Bác bỏ";
            Long reporterId = saved.getReporterId();
            Long rId = saved.getId();
            String note = saved.getDecisionNote();
            afterCommitExecutor.execute(() -> notificationService.notify(
                    reporterId,
                    NotificationType.REPORT_RESOLVED,
                    NotificationTemplates.reportResolvedTitle(rId),
                    NotificationTemplates.reportResolvedContent(rId, decisionStr, note),
                    "REPORT",
                    rId
            ));
        }

        return new ReportResolveResponse(
                saved.getId(),
                saved.getStatus(),
                request.decision(),
                saved.getDecisionNote(),
                saved.getResolvedBy(),
                saved.getResolvedAt()
        );
    }

    private ReportDetailResponse mapToDetailResponse(Report r) {
        ReportDetailResponse.ReporterSummary reporterSummary = userRepository.findById(r.getReporterId())
                .map(u -> new ReportDetailResponse.ReporterSummary(u.getId(), u.getFullName()))
                .orElse(null);

        ReportDetailResponse.ReportedUserSummary reportedUserSummary = r.getReportedUserId() != null
                ? userRepository.findById(r.getReportedUserId())
                .map(u -> new ReportDetailResponse.ReportedUserSummary(u.getId(), u.getFullName(), u.getStatus().name()))
                .orElse(null)
                : null;

        ReportDetailResponse.ReportedProductSummary reportedProductSummary = null;
        if (r.getReportedProductId() != null) {
            Product p = productRepository.findById(r.getReportedProductId()).orElse(null);
            if (p != null) {
                Long activeAuctionId = auctionRepository.findFirstByProductIdAndStatusIn(
                        p.getId(), List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE)
                ).map(Auction::getId).orElse(null);

                reportedProductSummary = new ReportDetailResponse.ReportedProductSummary(
                        p.getId(), p.getTitle(), p.getStatus().name(), activeAuctionId
                );
            }
        }

        ReportDetailResponse.ReportOrderSummary orderSummary = null;
        if (r.getReportedOrderId() != null) {
            Order o = orderRepository.findById(r.getReportedOrderId()).orElse(null);
            if (o != null) {
                orderSummary = new ReportDetailResponse.ReportOrderSummary(
                        o.getId(), o.getStatus().name()
                );
            }
        }

        return new ReportDetailResponse(
                r.getId(),
                r.getTargetType(),
                reporterSummary,
                reportedUserSummary,
                reportedProductSummary,
                orderSummary,
                r.getReason(),
                r.getStatus(),
                r.getDecisionNote(),
                r.getResolvedBy(),
                r.getResolvedAt(),
                r.getCreatedAt()
        );
    }

    private void requireAdmin(CurrentUser admin) {
        if (!admin.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Yêu cầu quyền quản trị viên");
        }
    }
}
