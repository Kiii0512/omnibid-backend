package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.common.PageResponse;
import com.omnibid.common.SortWhitelist;
import com.omnibid.dto.report.AdminReportDetailResponse;
import com.omnibid.dto.report.AdminReportItemResponse;
import com.omnibid.dto.report.ReportRequest;
import com.omnibid.dto.report.ReportResolveRequest;
import com.omnibid.dto.report.ReportResponse;
import com.omnibid.dto.report.ReportTargetSummary;
import com.omnibid.dto.user.UserRef;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.entity.Report;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.ReportDecision;
import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.ReportTargetType;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.ReportRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.NotificationService;
import com.omnibid.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt", "id", "status", "resolvedAt");

    private final ReportRepository reportRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AuctionRepository auctionRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    @Override
    @Transactional
    public ReportResponse createReport(CurrentUser currentUser, ReportRequest request) {
        if (request.reason() == null || request.reason().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Lý do báo cáo không được để trống");
        }
        String trimmedReason = request.reason().trim();
        if (trimmedReason.length() > 1000) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Lý do báo cáo tối đa 1000 ký tự");
        }
        if (request.reportedProductId() == null && request.reportedUserId() == null && request.reportedOrderId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phải chọn ít nhất một đối tượng báo cáo (sản phẩm, người dùng hoặc đơn hàng)");
        }

        if (request.reportedProductId() != null) {
            Product product = productRepository.findById(request.reportedProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm #" + request.reportedProductId()));
            if (product.getSellerId().equals(currentUser.id())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể tự báo cáo sản phẩm của chính mình");
            }
        }
        if (request.reportedUserId() != null) {
            if (request.reportedUserId().equals(currentUser.id())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể tự báo cáo chính mình");
            }
            if (!userRepository.existsById(request.reportedUserId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng #" + request.reportedUserId());
            }
        }
        if (request.reportedOrderId() != null) {
            Order order = orderRepository.findById(request.reportedOrderId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng #" + request.reportedOrderId()));
            if (!order.getBuyerId().equals(currentUser.id()) && !order.getSellerId().equals(currentUser.id())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không phải là bên tham gia đơn hàng này");
            }
        }

        Report report = Report.builder()
                .reporterId(currentUser.id())
                .reportedProductId(request.reportedProductId())
                .reportedUserId(request.reportedUserId())
                .reportedOrderId(request.reportedOrderId())
                .reason(trimmedReason)
                .status(ReportStatus.PENDING)
                .build();

        report = reportRepository.save(report);

        return new ReportResponse(
                report.getId(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminReportItemResponse> getAdminReports(
            CurrentUser admin, ReportStatus status, ReportTargetType targetType, Pageable pageable) {
        requireAdmin(admin);
        SortWhitelist.validate(pageable, ALLOWED_SORT_PROPERTIES);
        Pageable effective = normalizePageable(pageable);

        String targetTypeStr = targetType != null ? targetType.name() : null;
        Page<Report> page = reportRepository.findAdminReports(status, targetTypeStr, effective);

        return PageResponse.from(page, this::toAdminReportItem);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReportDetailResponse getAdminReportById(CurrentUser admin, Long reportId) {
        requireAdmin(admin);
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy báo cáo #" + reportId));
        return toAdminReportDetail(report);
    }

    @Override
    @Transactional
    public AdminReportDetailResponse resolveReport(CurrentUser admin, Long reportId, ReportResolveRequest request) {
        requireAdmin(admin);

        if (request.decision() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Quyết định xử lý không được để trống");
        }
        if (request.note() == null || request.note().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ghi chú xử lý không được để trống");
        }
        String note = request.note().trim();

        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy báo cáo #" + reportId));

        Instant now = Instant.now(clock);
        if (request.decision() == ReportDecision.RESOLVED) {
            report.resolve(note, admin.id(), now);
        } else {
            report.reject(note, admin.id(), now);
        }

        report = reportRepository.save(report);
        log.info("Admin {} action=REPORT_{} target=REPORT:{} note={}", admin.id(), report.getStatus(), report.getId(), note);

        notificationService.notify(
                report.getReporterId(),
                NotificationType.REPORT_RESOLVED,
                NotificationTemplates.reportResolvedTitle(report.getId(), report.getStatus()),
                NotificationTemplates.reportResolvedContent(report.getId(), note),
                "REPORT",
                report.getId()
        );

        return toAdminReportDetail(report);
    }

    private void requireAdmin(CurrentUser user) {
        if (user == null || !user.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Yêu cầu quyền quản trị viên");
        }
    }

    private Pageable normalizePageable(Pageable pageable) {
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.min(Math.max(1, pageable.getPageSize()), 100);
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "createdAt");
        return PageRequest.of(page, size, sort);
    }

    private AdminReportItemResponse toAdminReportItem(Report report) {
        return new AdminReportItemResponse(
                report.getId(),
                loadUserRef(report.getReporterId()),
                buildTargetSummary(report),
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }

    private AdminReportDetailResponse toAdminReportDetail(Report report) {
        return new AdminReportDetailResponse(
                report.getId(),
                loadUserRef(report.getReporterId()),
                buildTargetSummary(report),
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getAdminNote(),
                report.getResolvedBy() != null ? loadUserRef(report.getResolvedBy()) : null,
                report.getResolvedAt()
        );
    }

    private UserRef loadUserRef(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(u -> new UserRef(u.getId(), u.getFullName()))
                .orElse(null);
    }

    private ReportTargetSummary buildTargetSummary(Report report) {
        ReportTargetSummary.ProductTarget productTarget = null;
        if (report.getReportedProductId() != null) {
            Product product = productRepository.findById(report.getReportedProductId()).orElse(null);
            if (product != null) {
                ReportTargetSummary.ActiveAuctionRef activeAuction = auctionRepository
                        .findFirstByProductIdAndStatusIn(product.getId(), List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE))
                        .map(this::toActiveAuctionRef)
                        .orElse(null);

                productTarget = new ReportTargetSummary.ProductTarget(
                        product.getId(),
                        product.getTitle(),
                        product.getStatus(),
                        product.getSellerId(),
                        activeAuction
                );
            }
        }

        ReportTargetSummary.UserTarget userTarget = null;
        if (report.getReportedUserId() != null) {
            userTarget = userRepository.findById(report.getReportedUserId())
                    .map(u -> new ReportTargetSummary.UserTarget(
                            u.getId(),
                            u.getFullName(),
                            u.getEmail(),
                            u.getRole(),
                            u.getStatus()
                    ))
                    .orElse(null);
        }

        ReportTargetSummary.OrderTarget orderTarget = null;
        if (report.getReportedOrderId() != null) {
            orderTarget = orderRepository.findById(report.getReportedOrderId())
                    .map(o -> new ReportTargetSummary.OrderTarget(
                            o.getId(),
                            o.getStatus(),
                            o.getBuyerId(),
                            o.getSellerId(),
                            o.getAuctionId()
                    ))
                    .orElse(null);
        }

        return new ReportTargetSummary(productTarget, userTarget, orderTarget);
    }

    private ReportTargetSummary.ActiveAuctionRef toActiveAuctionRef(Auction auction) {
        return new ReportTargetSummary.ActiveAuctionRef(
                auction.getId(),
                auction.getStatus(),
                auction.getBidCount()
        );
    }
}
