package com.omnibid.service;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.dto.report.AdminReportDetailResponse;
import com.omnibid.dto.report.AdminReportItemResponse;
import com.omnibid.dto.report.ReportRequest;
import com.omnibid.dto.report.ReportResolveRequest;
import com.omnibid.dto.report.ReportResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.entity.Report;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.ReportDecision;
import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.ReportTargetType;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.ReportRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Long SELLER_ID = 1L;
    private static final Long BUYER_ID = 2L;
    private static final Long OTHER_ID = 3L;
    private static final Long ADMIN_ID = 9L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long AUCTION_ID = 200L;
    private static final Long ORDER_ID = 9L;
    private static final Long REPORT_ID = 21L;

    private final CurrentUser buyerUser = new CurrentUser(BUYER_ID, false);
    private final CurrentUser sellerUser = new CurrentUser(SELLER_ID, false);
    private final CurrentUser otherUser = new CurrentUser(OTHER_ID, false);
    private final CurrentUser adminUser = new CurrentUser(ADMIN_ID, true);

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private NotificationService notificationService;

    private ReportServiceImpl reportService;

    private User activeBuyer;
    private User activeSeller;
    private User activeAdmin;
    private Product product10;
    private Order order9;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(
                reportRepository,
                orderRepository,
                productRepository,
                userRepository,
                auctionRepository,
                notificationService,
                clock
        );

        activeBuyer = User.builder().id(BUYER_ID).fullName("Nguyen Buyer").email("buyer@example.com").role(UserRole.USER).status(UserStatus.ACTIVE).build();
        activeSeller = User.builder().id(SELLER_ID).fullName("Tran Seller").email("seller@example.com").role(UserRole.USER).status(UserStatus.ACTIVE).build();
        activeAdmin = User.builder().id(ADMIN_ID).fullName("Admin").email("admin@omnibid.local").role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        product10 = Product.builder().id(PRODUCT_ID).sellerId(SELLER_ID).title("Dong ho co").status(ProductStatus.AVAILABLE).build();
        order9 = Order.builder().id(ORDER_ID).auctionId(AUCTION_ID).buyerId(BUYER_ID).sellerId(SELLER_ID).productId(PRODUCT_ID).status(OrderStatus.SHIPPING).build();
    }

    @Test
    @DisplayName("createReport: báo cáo sản phẩm hợp lệ trả ReportResponse tối giản v4")
    void createReport_Product_Success() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product10));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            r.setId(REPORT_ID);
            r.setCreatedAt(NOW);
            return r;
        });

        ReportRequest request = new ReportRequest(PRODUCT_ID, null, null, "Mô tả sai thực tế");
        ReportResponse response = reportService.createReport(otherUser, request);

        assertThat(response.id()).isEqualTo(REPORT_ID);
        assertThat(response.status()).isEqualTo(ReportStatus.PENDING);
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("createReport: báo cáo đơn hàng chỉ cho phép bên tham gia, người ngoài bị 403")
    void createReport_Order_OnlyPartyAllowed() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order9));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            r.setId(REPORT_ID);
            r.setCreatedAt(NOW);
            return r;
        });

        ReportRequest request = new ReportRequest(null, null, ORDER_ID, "Hàng khác mô tả");
        ReportResponse ok = reportService.createReport(buyerUser, request);
        assertThat(ok.status()).isEqualTo(ReportStatus.PENDING);

        assertThatThrownBy(() -> reportService.createReport(otherUser, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("createReport: thiếu đối tượng, tự báo cáo, không tồn tại trả đúng mã lỗi")
    void createReport_ValidationErrors() {
        assertThatThrownBy(() -> reportService.createReport(otherUser, new ReportRequest(null, null, null, "Lý do")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        assertThatThrownBy(() -> reportService.createReport(otherUser, new ReportRequest(null, OTHER_ID, null, "Lý do")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product10));
        assertThatThrownBy(() -> reportService.createReport(sellerUser, new ReportRequest(PRODUCT_ID, null, null, "Lý do")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        when(productRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> reportService.createReport(otherUser, new ReportRequest(999L, null, null, "Lý do")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("getAdminReports & getAdminReportById: trả đầy đủ ReportTargetSummary đúng hợp đồng v4")
    void getAdminReports_AndDetail_ReturnsTargetSummary() {
        Report r = Report.builder()
                .id(REPORT_ID)
                .reporterId(BUYER_ID)
                .reportedProductId(PRODUCT_ID)
                .reportedUserId(SELLER_ID)
                .reportedOrderId(ORDER_ID)
                .reason("Vi phạm")
                .status(ReportStatus.PENDING)
                .createdAt(NOW)
                .build();

        when(reportRepository.findAdminReports(eq(ReportStatus.PENDING), eq("ORDER"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r)));
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(r));
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(activeBuyer));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(activeSeller));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product10));
        when(auctionRepository.findFirstByProductIdAndStatusIn(eq(PRODUCT_ID), anyList()))
                .thenReturn(Optional.of(Auction.builder().id(AUCTION_ID).status(AuctionStatus.ACTIVE).bidCount(3).build()));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order9));

        var page = reportService.getAdminReports(adminUser, ReportStatus.PENDING, ReportTargetType.ORDER, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        AdminReportItemResponse item = page.getContent().get(0);
        assertThat(item.reporter().id()).isEqualTo(BUYER_ID);
        assertThat(item.targets().product().activeAuction().id()).isEqualTo(AUCTION_ID);
        assertThat(item.targets().product().activeAuction().bidCount()).isEqualTo(3);
        assertThat(item.targets().user().email()).isEqualTo("seller@example.com");
        assertThat(item.targets().order().auctionId()).isEqualTo(AUCTION_ID);

        AdminReportDetailResponse detail = reportService.getAdminReportById(adminUser, REPORT_ID);
        assertThat(detail.id()).isEqualTo(REPORT_ID);
        assertThat(detail.adminNote()).isNull();
        assertThat(detail.resolvedBy()).isNull();
    }

    @Test
    @DisplayName("resolveReport: RESOLVED và REJECTED chỉ đổi trạng thái báo cáo và gửi REPORT_RESOLVED")
    void resolveReport_ResolveAndReject_Success() {
        Report pending = Report.builder()
                .id(REPORT_ID)
                .reporterId(BUYER_ID)
                .reportedProductId(PRODUCT_ID)
                .reason("Hàng giả")
                .status(ReportStatus.PENDING)
                .createdAt(NOW)
                .build();

        when(reportRepository.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(pending));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(activeBuyer));
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(activeAdmin));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product10));
        when(auctionRepository.findFirstByProductIdAndStatusIn(eq(PRODUCT_ID), anyList())).thenReturn(Optional.empty());

        AdminReportDetailResponse res = reportService.resolveReport(
                adminUser, REPORT_ID, new ReportResolveRequest(ReportDecision.RESOLVED, "Đã kiểm tra và xử lý")
        );

        assertThat(res.status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(res.adminNote()).isEqualTo("Đã kiểm tra và xử lý");
        assertThat(res.resolvedBy().id()).isEqualTo(ADMIN_ID);
        assertThat(res.resolvedAt()).isEqualTo(NOW);

        verify(notificationService).notify(
                eq(BUYER_ID), eq(NotificationType.REPORT_RESOLVED), anyString(), anyString(), eq("REPORT"), eq(REPORT_ID)
        );
    }

    @Test
    @DisplayName("resolveReport: báo cáo đã xử lý nhận 409 INVALID_STATUS_TRANSITION")
    void resolveReport_AlreadyResolved_Throws409() {
        Report resolved = Report.builder()
                .id(REPORT_ID)
                .reporterId(BUYER_ID)
                .status(ReportStatus.RESOLVED)
                .build();
        when(reportRepository.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(resolved));

        assertThatThrownBy(() -> reportService.resolveReport(
                adminUser, REPORT_ID, new ReportResolveRequest(ReportDecision.REJECTED, "Bác bỏ")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);

        verifyNoInteractions(notificationService);
    }
}
