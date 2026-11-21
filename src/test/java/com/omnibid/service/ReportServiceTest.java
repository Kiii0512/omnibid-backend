package com.omnibid.service;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.dto.report.ReportDetailResponse;
import com.omnibid.dto.report.ReportRequest;
import com.omnibid.dto.report.ReportResolveRequest;
import com.omnibid.dto.report.ReportResolveResponse;
import com.omnibid.dto.report.ReportResponse;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.entity.Report;
import com.omnibid.entity.User;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.*;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Long SELLER_ID = 1L;
    private static final Long BUYER_ID = 2L;
    private static final Long OTHER_ID = 3L;
    private static final Long ADMIN_ID = 9L;
    private static final Long PRODUCT_ID = 10L;
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
                clock,
                notificationService,
                null
        );

        activeBuyer = User.builder().id(BUYER_ID).fullName("Nguyen Buyer").email("buyer@test.com").status(UserStatus.ACTIVE).role(UserRole.USER).build();
        activeSeller = User.builder().id(SELLER_ID).fullName("Tran Seller").email("seller@test.com").status(UserStatus.ACTIVE).role(UserRole.USER).build();
        product10 = Product.builder().id(PRODUCT_ID).sellerId(SELLER_ID).title("Sản phẩm 10").build();
        order9 = Order.builder().id(ORDER_ID).buyerId(BUYER_ID).sellerId(SELLER_ID).status(OrderStatus.SHIPPING).build();
    }

    @Test
    @DisplayName("createReport — thành công tạo báo cáo sản phẩm")
    void createReport_ProductReport_Success() {
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(activeBuyer));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product10));

        Report saved = Report.builder()
                .id(REPORT_ID)
                .reporterId(BUYER_ID)
                .reportedProductId(PRODUCT_ID)
                .reason("Sản phẩm có dấu hiệu giả mạo nhãn hiệu")
                .status(ReportStatus.PENDING)
                .createdAt(NOW)
                .build();
        when(reportRepository.save(any(Report.class))).thenReturn(saved);

        ReportRequest req = new ReportRequest(PRODUCT_ID, null, null, "Sản phẩm có dấu hiệu giả mạo nhãn hiệu");
        ReportResponse res = reportService.createReport(buyerUser, req);

        assertNotNull(res);
        assertEquals(REPORT_ID, res.id());
        assertEquals(ReportStatus.PENDING, res.status());
    }

    @Test
    @DisplayName("createReport — tự báo cáo chính mình bị VALIDATION_ERROR")
    void createReport_SelfReport_ThrowsValidationError() {
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(activeBuyer));

        ReportRequest req = new ReportRequest(null, BUYER_ID, null, "Tự báo cáo bản thân mình");
        assertThatThrownBy(() -> reportService.createReport(buyerUser, req))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("createReport — báo cáo đơn hàng khi không phải bên liên quan bị 403 FORBIDDEN")
    void createReport_OrderReport_OtherUser_ThrowsForbidden() {
        when(userRepository.findById(OTHER_ID)).thenReturn(Optional.of(activeSeller));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order9));

        ReportRequest req = new ReportRequest(null, null, ORDER_ID, "Báo cáo đơn không phải của mình");
        assertThatThrownBy(() -> reportService.createReport(otherUser, req))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("resolveReport — Admin giải quyết báo cáo RESOLVED kèm note thành công")
    void resolveReport_Success() {
        Report report = Report.builder()
                .id(REPORT_ID)
                .reporterId(BUYER_ID)
                .status(ReportStatus.PENDING)
                .reason("Báo cáo vi phạm")
                .createdAt(NOW)
                .build();
        when(reportRepository.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArgument(0));

        ReportResolveRequest request = new ReportResolveRequest(com.omnibid.enums.ReportDecision.RESOLVED, "Đã xác minh và xử lý vi phạm");
        ReportResolveResponse res = reportService.resolveReport(adminUser, REPORT_ID, request);

        assertNotNull(res);
        assertEquals(ReportStatus.RESOLVED, report.getStatus());
        assertEquals("Đã xác minh và xử lý vi phạm", report.getAdminNote());
        assertEquals(ADMIN_ID, report.getResolvedBy());
        assertEquals(NOW, report.getResolvedAt());
    }

    @Test
    @DisplayName("resolveReport — Báo cáo đã giải quyết rồi xử lý lại bị INVALID_STATUS_TRANSITION")
    void resolveReport_AlreadyResolved_ThrowsConflict() {
        Report report = Report.builder()
                .id(REPORT_ID)
                .reporterId(BUYER_ID)
                .status(ReportStatus.RESOLVED)
                .build();
        when(reportRepository.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report));

        ReportResolveRequest request = new ReportResolveRequest(com.omnibid.enums.ReportDecision.RESOLVED, "Ghi chú");
        assertThatThrownBy(() -> reportService.resolveReport(adminUser, REPORT_ID, request))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.INVALID_STATUS_TRANSITION);
    }
}
