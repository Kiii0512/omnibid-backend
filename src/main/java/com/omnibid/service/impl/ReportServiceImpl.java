package com.omnibid.service.impl;

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
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    @Override
    @Transactional
    public ReportResponse createReport(CurrentUser currentUser, ReportRequest request) {
        // TODO v4-phase-7: refine report validation rules per API_SPEC v4
        if (request.reason() == null || request.reason().trim().length() < 10) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Reason must be at least 10 characters");
        }
        if (request.reportedUserId() == null && request.reportedProductId() == null && request.reportedOrderId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "At least one target (user, product, or order) is required");
        }

        if (request.reportedUserId() != null) {
            if (request.reportedUserId().equals(currentUser.id())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cannot report self");
            }
            if (!userRepository.existsById(request.reportedUserId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Reported user not found");
            }
        }
        if (request.reportedProductId() != null) {
            Product product = productRepository.findById(request.reportedProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Reported product not found"));
            if (product.getSellerId().equals(currentUser.id())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cannot report own product");
            }
        }
        if (request.reportedOrderId() != null) {
            Order order = orderRepository.findById(request.reportedOrderId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Reported order not found"));
            if (!order.getBuyerId().equals(currentUser.id()) && !order.getSellerId().equals(currentUser.id())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "You are not a party to this order");
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

        report = reportRepository.save(report);

        return new ReportResponse(
                report.getId(),
                report.getReportedOrderId(),
                report.getReportedUserId(),
                report.getReportedProductId(),
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportDetailResponse> getAdminReports(CurrentUser admin, ReportStatus status, ReportTargetType targetType, Pageable pageable) {
        if (!admin.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Admin required");
        }

        String targetTypeStr = targetType != null ? targetType.name() : null;
        Page<Report> page = reportRepository.findAdminReports(status, targetTypeStr, pageable);

        return PageResponse.from(page, r -> {
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
                    String paymentStatus = paymentRepository.findByOrderIdOrderByAttemptNoDesc(o.getId())
                            .stream().findFirst().map(pay -> pay.getStatus().name()).orElse(null);

                    orderSummary = new ReportDetailResponse.ReportOrderSummary(
                            o.getId(), o.getStatus(), paymentStatus
                    );
                }
            }

            return new ReportDetailResponse(
                    r.getId(),
                    reporterSummary,
                    reportedUserSummary,
                    reportedProductSummary,
                    orderSummary,
                    r.getReason(),
                    r.getStatus(),
                    r.getAdminNote(),
                    r.getResolvedBy(),
                    r.getResolvedAt(),
                    r.getCreatedAt()
            );
        });
    }

    @Override
    @Transactional
    public ReportResolveResponse resolveReport(CurrentUser admin, Long reportId, ReportResolveRequest request) {
        if (!admin.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Admin required");
        }

        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Report not found"));

        if (request.note() == null || request.note().trim().length() < 5) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Note must be at least 5 characters");
        }

        String note = request.note().trim();
        Instant now = Instant.now(clock);

        if (request.decision() == ReportDecision.RESOLVED) {
            report.resolve(note, admin.id(), now);
        } else {
            report.reject(note, admin.id(), now);
        }

        report = reportRepository.save(report);
        log.info("Admin {} action=REPORT_{} target=REPORT:{} note={}", admin.id(), report.getStatus(), report.getId(), note);

        notificationService.notify(report.getReporterId(), NotificationType.REPORT_RESOLVED,
                NotificationTemplates.reportResolvedTitle(report.getId(), report.getStatus().name()),
                NotificationTemplates.reportResolvedContent(report.getId(), note),
                "REPORT", report.getId());

        return new ReportResolveResponse(
                report.getId(),
                report.getStatus(),
                report.getAdminNote(),
                report.getResolvedBy(),
                report.getResolvedAt()
        );
    }
}
