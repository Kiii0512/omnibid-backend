package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.report.*;
import com.omnibid.entity.*;
import com.omnibid.enums.*;
import com.omnibid.repository.*;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.AdminService;
import com.omnibid.service.AuditService;
import com.omnibid.service.NotificationService;
import com.omnibid.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AuctionRepository auctionRepository;
    private final ShipmentRepository shipmentRepository;
    private final PaymentRepository paymentRepository;
    private final AdminService adminService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final Clock clock;

    @Override
    @Transactional
    public ReportResponse createReport(CurrentUser currentUser, ReportRequest request) {
        if (request.reason() == null || request.reason().trim().length() < 10) {
            throw new AppException("VALIDATION_ERROR", "Reason must be at least 10 characters", HttpStatus.BAD_REQUEST);
        }

        if (request.reportType() == ReportType.COMPLAINT) {
            return createComplaintReport(currentUser, request);
        }

        User reporter = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new AppException("NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        if (reporter.getStatus() == UserStatus.BANNED) {
            throw new AppException("ACCOUNT_BANNED", "Account is banned", HttpStatus.FORBIDDEN);
        }

        if (request.reportType() == ReportType.VIOLATION) {
            return createViolationReport(currentUser, request);
        } else {
            throw new AppException("VALIDATION_ERROR", "Unsupported report type: " + request.reportType(), HttpStatus.BAD_REQUEST);
        }
    }

    private ReportResponse createViolationReport(CurrentUser currentUser, ReportRequest request) {
        if (request.reportedUserId() == null && request.reportedProductId() == null) {
            throw new AppException("VALIDATION_ERROR", "Either reportedUserId or reportedProductId is required", HttpStatus.BAD_REQUEST);
        }

        if (request.reportedUserId() != null) {
            if (request.reportedUserId().equals(currentUser.id())) {
                throw new AppException("VALIDATION_ERROR", "Cannot report self", HttpStatus.BAD_REQUEST);
            }
            if (!userRepository.existsById(request.reportedUserId())) {
                throw new AppException("NOT_FOUND", "Reported user not found", HttpStatus.NOT_FOUND);
            }
        }

        if (request.reportedProductId() != null) {
            Product product = productRepository.findById(request.reportedProductId())
                    .orElseThrow(() -> new AppException("NOT_FOUND", "Reported product not found", HttpStatus.NOT_FOUND));
            if (product.getSellerId().equals(currentUser.id())) {
                throw new AppException("VALIDATION_ERROR", "Cannot report own product", HttpStatus.BAD_REQUEST);
            }
        }

        if (request.reportedProductId() != null
                && reportRepository.existsByReporterIdAndReportedProductIdAndStatus(currentUser.id(), request.reportedProductId(), ReportStatus.PENDING)) {
            throw new AppException("DUPLICATE_PENDING_REPORT", "A pending report already exists for this product", HttpStatus.CONFLICT);
        }

        if (request.reportedUserId() != null
                && reportRepository.existsByReporterIdAndReportedUserIdAndStatus(currentUser.id(), request.reportedUserId(), ReportStatus.PENDING)) {
            throw new AppException("DUPLICATE_PENDING_REPORT", "A pending report already exists for this user", HttpStatus.CONFLICT);
        }

        Report report = Report.builder()
                .reporterId(currentUser.id())
                .reportType(ReportType.VIOLATION)
                .reportedUserId(request.reportedUserId())
                .reportedProductId(request.reportedProductId())
                .reason(request.reason().trim())
                .status(ReportStatus.PENDING)
                .build();

        report = reportRepository.save(report);

        return new ReportResponse(
                report.getId(),
                report.getReportType(),
                null,
                report.getReportedUserId(),
                report.getReportedProductId(),
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }

    private ReportResponse createComplaintReport(CurrentUser currentUser, ReportRequest request) {
        if (request.orderId() == null) {
            throw new AppException("VALIDATION_ERROR", "orderId is required for complaint", HttpStatus.BAD_REQUEST);
        }

        // Lock hierarchy: L4 Order - lock Order first before non-locking reads to preserve REPEATABLE READ snapshot
        Order order = orderRepository.findByIdForUpdate(request.orderId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND));

        User reporter = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new AppException("NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        if (reporter.getStatus() == UserStatus.BANNED) {
            throw new AppException("ACCOUNT_BANNED", "Account is banned", HttpStatus.FORBIDDEN);
        }

        if (!order.getBuyerId().equals(currentUser.id())) {
            throw new AppException("FORBIDDEN", "Only buyer can file complaint", HttpStatus.FORBIDDEN);
        }

        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.SHIPPING) {
            throw new AppException("COMPLAINT_WINDOW_CLOSED",
                    "Complaint window is closed for order in status: " + order.getStatus(), HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (order.getConfirmDeadline() != null && !order.getConfirmDeadline().isAfter(now)) {
            throw new AppException("COMPLAINT_WINDOW_CLOSED", "Complaint deadline has expired", HttpStatus.CONFLICT);
        }

        if (reportRepository.existsByReporterIdAndOrderIdAndStatus(currentUser.id(), order.getId(), ReportStatus.PENDING)) {
            throw new AppException("DUPLICATE_PENDING_REPORT", "A pending complaint already exists for this order", HttpStatus.CONFLICT);
        }

        Report report = Report.builder()
                .reporterId(currentUser.id())
                .reportType(ReportType.COMPLAINT)
                .orderId(order.getId())
                .reason(request.reason().trim())
                .status(ReportStatus.PENDING)
                .build();

        report = reportRepository.save(report);

        // Notify seller (N-15)
        notificationService.notify(order.getSellerId(), NotificationType.ORDER_UPDATE,
                NotificationTemplates.orderComplaintSubmittedTitle(order.getId()),
                NotificationTemplates.orderComplaintSubmittedContent(order.getId()),
                "ORDER", order.getId());

        return new ReportResponse(
                report.getId(),
                report.getReportType(),
                report.getOrderId(),
                null,
                null,
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportDetailResponse> getAdminReports(CurrentUser admin, ReportStatus status, ReportType reportType, Pageable pageable) {
        if (!admin.isAdmin()) {
            throw new AppException("FORBIDDEN", "Admin required", HttpStatus.FORBIDDEN);
        }

        Page<Report> page = reportRepository.findAdminReports(status, reportType, pageable);

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
            if (r.getOrderId() != null) {
                Order o = orderRepository.findById(r.getOrderId()).orElse(null);
                if (o != null) {
                    String shipmentStatus = shipmentRepository.findByOrderId(o.getId())
                            .map(s -> s.getStatus().name()).orElse(null);
                    String paymentStatus = paymentRepository.findByOrderIdOrderByAttemptNoDesc(o.getId())
                            .stream().findFirst().map(pay -> pay.getStatus().name()).orElse(null);

                    orderSummary = new ReportDetailResponse.ReportOrderSummary(
                            o.getId(), o.getStatus(), shipmentStatus, paymentStatus
                    );
                }
            }

            return new ReportDetailResponse(
                    r.getId(),
                    r.getReportType(),
                    reporterSummary,
                    reportedUserSummary,
                    reportedProductSummary,
                    orderSummary,
                    r.getReason(),
                    r.getStatus(),
                    r.getAction(),
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
            throw new AppException("FORBIDDEN", "Admin required", HttpStatus.FORBIDDEN);
        }

        // Lock hierarchy: L0 Report
        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Report not found", HttpStatus.NOT_FOUND));

        if (report.getStatus() != ReportStatus.PENDING) {
            throw new AppException("INVALID_STATUS_TRANSITION",
                    "Report has already been resolved or rejected", HttpStatus.CONFLICT);
        }

        if (request.note() == null || request.note().trim().length() < 5) {
            throw new AppException("VALIDATION_ERROR", "Note must be at least 5 characters", HttpStatus.BAD_REQUEST);
        }

        String note = request.note().trim();
        ReportAction action = request.action();

        if (action == ReportAction.NONE) {
            report.setStatus(ReportStatus.REJECTED);
            report.setAction(ReportAction.NONE);
            report.setResolvedBy(admin.id());
            report.setResolvedAt(LocalDateTime.now(clock));
            report.setAdminNote(note);
            report = reportRepository.save(report);

            auditService.log(admin.id(), "REPORT_REJECT", "REPORT", report.getId(), note);

            notificationService.notify(report.getReporterId(), NotificationType.REPORT_RESOLVED,
                    NotificationTemplates.reportResolvedTitle(report.getId(), "Bác bỏ"),
                    NotificationTemplates.reportResolvedContent(report.getId(), note),
                    "REPORT", report.getId());

            return new ReportResolveResponse(report.getId(), report.getStatus(), report.getAction(),
                    report.getResolvedBy(), report.getResolvedAt(), Map.of());
        }

        // Validate action suitability before execution
        if (report.getReportType() == ReportType.VIOLATION) {
            if (action != ReportAction.BAN_USER && action != ReportAction.HIDE_PRODUCT && action != ReportAction.CANCEL_AUCTION) {
                throw new AppException("VALIDATION_ERROR", "Invalid action for VIOLATION: " + action, HttpStatus.BAD_REQUEST);
            }
            if (action == ReportAction.BAN_USER && report.getReportedUserId() == null) {
                throw new AppException("VALIDATION_ERROR", "Reported user is required for BAN_USER", HttpStatus.BAD_REQUEST);
            }
            if (action == ReportAction.HIDE_PRODUCT && report.getReportedProductId() == null) {
                throw new AppException("VALIDATION_ERROR", "Reported product is required for HIDE_PRODUCT", HttpStatus.BAD_REQUEST);
            }
            if (action == ReportAction.CANCEL_AUCTION) {
                if (report.getReportedProductId() == null) {
                    throw new AppException("VALIDATION_ERROR", "Reported product is required for CANCEL_AUCTION", HttpStatus.BAD_REQUEST);
                }
                boolean hasOpenAuction = auctionRepository.findFirstByProductIdAndStatusIn(
                        report.getReportedProductId(), List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE)
                ).isPresent();
                if (!hasOpenAuction) {
                    throw new AppException("INVALID_STATUS_TRANSITION", "Product has no open auction to cancel", HttpStatus.CONFLICT);
                }
            }
        } else if (report.getReportType() == ReportType.COMPLAINT) {
            if (action != ReportAction.COMPLETE_ORDER && action != ReportAction.CANCEL_ORDER) {
                throw new AppException("VALIDATION_ERROR", "Invalid action for COMPLAINT: " + action, HttpStatus.BAD_REQUEST);
            }
            if (report.getOrderId() == null) {
                throw new AppException("VALIDATION_ERROR", "Order is required for COMPLAINT", HttpStatus.BAD_REQUEST);
            }
        }

        // Execute sub-action FIRST (UT-RPT-14: if sub-action fails, report stays PENDING)
        Map<String, Object> effects = switch (action) {
            case BAN_USER -> adminService.banUser(report.getReportedUserId(), note, admin.id());
            case HIDE_PRODUCT -> adminService.hideProduct(report.getReportedProductId(), note, admin.id());
            case CANCEL_AUCTION -> {
                Auction openAuction = auctionRepository.findFirstByProductIdAndStatusIn(
                        report.getReportedProductId(), List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE)
                ).orElseThrow(() -> new AppException("INVALID_STATUS_TRANSITION", "Product has no open auction to cancel", HttpStatus.CONFLICT));
                yield adminService.cancelAuction(openAuction.getId(), note, admin.id());
            }
            case COMPLETE_ORDER -> adminService.completeOrder(report.getOrderId(), note, admin.id());
            case CANCEL_ORDER -> adminService.cancelOrder(report.getOrderId(), note, admin.id());
            default -> throw new AppException("VALIDATION_ERROR", "Unsupported action: " + action, HttpStatus.BAD_REQUEST);
        };

        // Update Report state after sub-action succeeds
        report.setStatus(ReportStatus.RESOLVED);
        report.setAction(action);
        report.setResolvedBy(admin.id());
        report.setResolvedAt(LocalDateTime.now(clock));
        report.setAdminNote(note);
        report = reportRepository.save(report);

        auditService.log(admin.id(), "REPORT_RESOLVE", "REPORT", report.getId(), note);

        notificationService.notify(report.getReporterId(), NotificationType.REPORT_RESOLVED,
                NotificationTemplates.reportResolvedTitle(report.getId(), "Chấp nhận"),
                NotificationTemplates.reportResolvedContent(report.getId(), note),
                "REPORT", report.getId());

        return new ReportResolveResponse(report.getId(), report.getStatus(), report.getAction(),
                report.getResolvedBy(), report.getResolvedAt(), effects);
    }
}
