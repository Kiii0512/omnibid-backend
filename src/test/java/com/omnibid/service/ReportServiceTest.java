package com.omnibid.service;

import com.omnibid.common.AppException;
import com.omnibid.dto.report.ReportRequest;
import com.omnibid.dto.report.ReportResolveRequest;
import com.omnibid.dto.report.ReportResolveResponse;
import com.omnibid.dto.report.ReportResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.entity.Report;
import com.omnibid.entity.User;
import com.omnibid.enums.*;
import com.omnibid.repository.*;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.impl.AdminServiceImpl;
import com.omnibid.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));

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
    private ShipmentRepository shipmentRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private AdminService adminService;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;

    private ReportServiceImpl reportService;

    private User activeBuyer;
    private User activeSeller;
    private User activeOther;
    private Product product10;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(
                reportRepository,
                orderRepository,
                productRepository,
                userRepository,
                auctionRepository,
                shipmentRepository,
                paymentRepository,
                adminService,
                auditService,
                notificationService,
                clock
        );

        activeBuyer = User.builder().id(BUYER_ID).fullName("Nguyen Buyer").status(UserStatus.ACTIVE).build();
        activeSeller = User.builder().id(SELLER_ID).fullName("Tran Seller").status(UserStatus.ACTIVE).build();
        activeOther = User.builder().id(OTHER_ID).fullName("Le Other").status(UserStatus.ACTIVE).build();
        product10 = Product.builder().id(PRODUCT_ID).sellerId(SELLER_ID).title("Dong ho co").status(ProductStatus.AVAILABLE).build();
    }

    @Test
    @DisplayName("UT-RPT-01: Báo cáo vi phạm sản phẩm — OTHER báo cáo PRODUCT, reason 10 ký tự")
    void createReport_ValidProductViolation_Success() {
        when(userRepository.findById(OTHER_ID)).thenReturn(Optional.of(activeOther));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product10));
        when(reportRepository.existsByReporterIdAndReportedProductIdAndStatus(OTHER_ID, PRODUCT_ID, ReportStatus.PENDING))
                .thenReturn(false);

        Report saved = Report.builder()
                .id(REPORT_ID)
                .reporterId(OTHER_ID)
                .reportType(ReportType.VIOLATION)
                .reportedProductId(PRODUCT_ID)
                .reason("Mô tả sai thực tế")
                .status(ReportStatus.PENDING)
                .createdAt(NOW)
                .build();
        when(reportRepository.save(any(Report.class))).thenReturn(saved);

        ReportRequest request = new ReportRequest(ReportType.VIOLATION, null, PRODUCT_ID, null, "Mô tả sai thực tế");
        ReportResponse response = reportService.createReport(otherUser, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(REPORT_ID);
        assertThat(response.reportType()).isEqualTo(ReportType.VIOLATION);
        assertThat(response.status()).isEqualTo(ReportStatus.PENDING);
        assertThat(response.reportedProductId()).isEqualTo(PRODUCT_ID);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getReportedProductId()).isEqualTo(PRODUCT_ID);
        assertThat(captor.getValue().getReportType()).isEqualTo(ReportType.VIOLATION);
        assertThat(captor.getValue().getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    @DisplayName("UT-RPT-02: Báo cáo vi phạm không hợp lệ — thiếu đối tượng, tự báo cáo, reason < 10, không tồn tại")
    void createReport_InvalidViolation_ThrowsCorrectError() {
        when(userRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            if (id.equals(OTHER_ID)) return Optional.of(activeOther);
            if (id.equals(SELLER_ID)) return Optional.of(activeSeller);
            return Optional.empty();
        });

        // Case 1: thiếu đối tượng
        ReportRequest reqNoTarget = new ReportRequest(ReportType.VIOLATION, null, null, null, "1234567890");
        assertThatThrownBy(() -> reportService.createReport(otherUser, reqNoTarget))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Either reportedUserId or reportedProductId is required")
                .matches(e -> ((AppException) e).getCode().equals("VALIDATION_ERROR"));

        // Case 2: SELLER báo cáo sản phẩm của mình
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product10));
        ReportRequest reqOwnProduct = new ReportRequest(ReportType.VIOLATION, null, PRODUCT_ID, null, "1234567890");
        assertThatThrownBy(() -> reportService.createReport(sellerUser, reqOwnProduct))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Cannot report own product")
                .matches(e -> ((AppException) e).getCode().equals("VALIDATION_ERROR"));

        // Case 3: OTHER báo cáo chính mình
        ReportRequest reqSelf = new ReportRequest(ReportType.VIOLATION, OTHER_ID, null, null, "1234567890");
        assertThatThrownBy(() -> reportService.createReport(otherUser, reqSelf))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Cannot report self")
                .matches(e -> ((AppException) e).getCode().equals("VALIDATION_ERROR"));

        // Case 4: reason 9 ký tự
        ReportRequest reqShortReason = new ReportRequest(ReportType.VIOLATION, null, PRODUCT_ID, null, "123456789");
        assertThatThrownBy(() -> reportService.createReport(otherUser, reqShortReason))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("VALIDATION_ERROR"));

        // Case 5: sản phẩm id không tồn tại
        when(productRepository.findById(999L)).thenReturn(Optional.empty());
        ReportRequest reqNotFoundProduct = new ReportRequest(ReportType.VIOLATION, null, 999L, null, "1234567890");
        assertThatThrownBy(() -> reportService.createReport(otherUser, reqNotFoundProduct))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("NOT_FOUND") && ((AppException) e).getStatus() == HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("UT-RPT-03: Khiếu nại hợp lệ — BUYER, ORDER SHIPPING, confirm_deadline = NOW + 1 ngày")
    void createReport_ValidComplaint_SuccessAndNotifySeller() {
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(activeBuyer));
        Order order = Order.builder()
                .id(ORDER_ID)
                .buyerId(BUYER_ID)
                .sellerId(SELLER_ID)
                .productId(PRODUCT_ID)
                .status(OrderStatus.SHIPPING)
                .confirmDeadline(NOW.plusDays(1))
                .build();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(reportRepository.existsByReporterIdAndOrderIdAndStatus(BUYER_ID, ORDER_ID, ReportStatus.PENDING))
                .thenReturn(false);

        Report saved = Report.builder()
                .id(REPORT_ID)
                .reporterId(BUYER_ID)
                .reportType(ReportType.COMPLAINT)
                .orderId(ORDER_ID)
                .reason("Hang nhan duoc khac mo ta")
                .status(ReportStatus.PENDING)
                .createdAt(NOW)
                .build();
        when(reportRepository.save(any(Report.class))).thenReturn(saved);

        ReportRequest request = new ReportRequest(ReportType.COMPLAINT, null, null, ORDER_ID, "Hang nhan duoc khac mo ta");
        ReportResponse response = reportService.createReport(buyerUser, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(REPORT_ID);
        assertThat(response.reportType()).isEqualTo(ReportType.COMPLAINT);
        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.status()).isEqualTo(ReportStatus.PENDING);

        // N-15: ORDER_UPDATE cho seller
        verify(notificationService).notify(eq(SELLER_ID), eq(NotificationType.ORDER_UPDATE),
                contains("khiếu nại đang chờ xử lý"), anyString(), eq("ORDER"), eq(ORDER_ID));
    }

    @Test
    @DisplayName("UT-RPT-04: Khiếu nại bởi người khác — SELLER, OTHER -> 403 FORBIDDEN")
    void createReport_ComplaintByNonBuyer_ThrowsForbidden() {
        Order order = Order.builder()
                .id(ORDER_ID)
                .buyerId(BUYER_ID)
                .sellerId(SELLER_ID)
                .productId(PRODUCT_ID)
                .status(OrderStatus.SHIPPING)
                .confirmDeadline(NOW.plusDays(1))
                .build();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(activeSeller));
        when(userRepository.findById(OTHER_ID)).thenReturn(Optional.of(activeOther));

        ReportRequest request = new ReportRequest(ReportType.COMPLAINT, null, null, ORDER_ID, "Hang khong dung mo ta 123");

        // Seller
        assertThatThrownBy(() -> reportService.createReport(sellerUser, request))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("FORBIDDEN") && ((AppException) e).getStatus() == HttpStatus.FORBIDDEN);

        // Other
        assertThatThrownBy(() -> reportService.createReport(otherUser, request))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("FORBIDDEN") && ((AppException) e).getStatus() == HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("UT-RPT-05: Cửa sổ khiếu nại biên thời gian — confirm_deadline = NOW (hết) -> 409; NOW + 1s (còn) -> thành công; null với PAID -> mở")
    void createReport_ComplaintWindowBoundary_EdgeCases() {
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(activeBuyer));

        // Case 1: confirm_deadline = NOW (đã hết)
        Order expiredOrder = Order.builder()
                .id(ORDER_ID)
                .buyerId(BUYER_ID)
                .sellerId(SELLER_ID)
                .productId(PRODUCT_ID)
                .status(OrderStatus.SHIPPING)
                .confirmDeadline(NOW)
                .build();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(expiredOrder));

        ReportRequest request = new ReportRequest(ReportType.COMPLAINT, null, null, ORDER_ID, "Hang hong hoc phu kien");
        assertThatThrownBy(() -> reportService.createReport(buyerUser, request))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("COMPLAINT_WINDOW_CLOSED") && ((AppException) e).getStatus() == HttpStatus.CONFLICT);

        // Case 2: confirm_deadline = NOW + 1s (còn)
        Order validOrder = Order.builder()
                .id(ORDER_ID)
                .buyerId(BUYER_ID)
                .sellerId(SELLER_ID)
                .productId(PRODUCT_ID)
                .status(OrderStatus.SHIPPING)
                .confirmDeadline(NOW.plusSeconds(1))
                .build();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(validOrder));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportResponse resValid = reportService.createReport(buyerUser, request);
        assertThat(resValid.status()).isEqualTo(ReportStatus.PENDING);

        // Case 3: confirm_deadline = null với Order PAID (mở)
        Order paidOrder = Order.builder()
                .id(ORDER_ID)
                .buyerId(BUYER_ID)
                .sellerId(SELLER_ID)
                .productId(PRODUCT_ID)
                .status(OrderStatus.PAID)
                .confirmDeadline(null)
                .build();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(paidOrder));

        ReportResponse resPaid = reportService.createReport(buyerUser, request);
        assertThat(resPaid.status()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    @DisplayName("UT-RPT-06: Cửa sổ khiếu nại ở trạng thái — Order PENDING_PAYMENT, COMPLETED, CANCELLED, EXPIRED -> 409 COMPLAINT_WINDOW_CLOSED")
    void createReport_InvalidOrderStatuses_ThrowsComplaintWindowClosed() {
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(activeBuyer));
        ReportRequest request = new ReportRequest(ReportType.COMPLAINT, null, null, ORDER_ID, "Hang khong dung mo ta 123");

        for (OrderStatus invalidStatus : List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.EXPIRED)) {
            Order order = Order.builder()
                    .id(ORDER_ID)
                    .buyerId(BUYER_ID)
                    .sellerId(SELLER_ID)
                    .productId(PRODUCT_ID)
                    .status(invalidStatus)
                    .build();
            when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> reportService.createReport(buyerUser, request))
                    .isInstanceOf(AppException.class)
                    .matches(e -> ((AppException) e).getCode().equals("COMPLAINT_WINDOW_CLOSED") && ((AppException) e).getStatus() == HttpStatus.CONFLICT);
        }
    }

    @Test
    @DisplayName("UT-RPT-07: Trùng báo cáo PENDING — BUYER đã có COMPLAINT PENDING -> 409; sau khi REJECTED thì gửi lại được")
    void createReport_DuplicatePendingComplaint_Throws409_AndAllowsAfterResolved() {
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(activeBuyer));
        Order order = Order.builder()
                .id(ORDER_ID)
                .buyerId(BUYER_ID)
                .sellerId(SELLER_ID)
                .productId(PRODUCT_ID)
                .status(OrderStatus.SHIPPING)
                .confirmDeadline(NOW.plusDays(1))
                .build();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        when(reportRepository.existsByReporterIdAndOrderIdAndStatus(BUYER_ID, ORDER_ID, ReportStatus.PENDING))
                .thenReturn(true);

        ReportRequest request = new ReportRequest(ReportType.COMPLAINT, null, null, ORDER_ID, "Khiếu nại lần 1 bị trùng");
        assertThatThrownBy(() -> reportService.createReport(buyerUser, request))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("DUPLICATE_PENDING_REPORT") && ((AppException) e).getStatus() == HttpStatus.CONFLICT);

        // Sau khi báo cáo cũ đã xử lý (không còn PENDING)
        when(reportRepository.existsByReporterIdAndOrderIdAndStatus(BUYER_ID, ORDER_ID, ReportStatus.PENDING))
                .thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportResponse response = reportService.createReport(buyerUser, request);
        assertThat(response.status()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    @DisplayName("UT-RPT-08: Xử lý COMPLETE_ORDER — COMPLAINT PENDING, Order SHIPPING -> Order COMPLETED, Product SOLD; Report RESOLVED")
    void resolveReport_CompleteOrder_Success() {
        Report report = Report.builder()
                .id(REPORT_ID)
                .reporterId(BUYER_ID)
                .reportType(ReportType.COMPLAINT)
                .orderId(ORDER_ID)
                .status(ReportStatus.PENDING)
                .build();
        when(reportRepository.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        when(adminService.completeOrder(ORDER_ID, "Hop le, hoan tat don", ADMIN_ID))
                .thenReturn(Map.of("orderStatus", "COMPLETED", "productStatus", "SOLD"));

        ReportResolveRequest request = new ReportResolveRequest(ReportAction.COMPLETE_ORDER, "Hop le, hoan tat don");
        ReportResolveResponse response = reportService.resolveReport(adminUser, REPORT_ID, request);

        assertThat(response.status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(response.action()).isEqualTo(ReportAction.COMPLETE_ORDER);
        assertThat(response.resolvedBy()).isEqualTo(ADMIN_ID);
        assertThat(response.resolvedAt()).isEqualTo(NOW);
        assertThat(response.effects()).containsEntry("orderStatus", "COMPLETED");

        verify(adminService).completeOrder(ORDER_ID, "Hop le, hoan tat don", ADMIN_ID);
        verify(auditService).log(ADMIN_ID, "REPORT_RESOLVE", "REPORT", REPORT_ID, "Hop le, hoan tat don");
        verify(notificationService).notify(eq(BUYER_ID), eq(NotificationType.REPORT_RESOLVED),
                contains("Chấp nhận"), anyString(), eq("REPORT"), eq(REPORT_ID));
    }

    @Test
    @DisplayName("UT-RPT-09: Xử lý CANCEL_ORDER — COMPLAINT PENDING, Order PAID -> Order CANCELLED; Product AVAILABLE; Report RESOLVED")
    void resolveReport_CancelOrder_Success() {
        Report report = Report.builder()
                .id(REPORT_ID)
                .reporterId(BUYER_ID)
                .reportType(ReportType.COMPLAINT)
                .orderId(ORDER_ID)
                .status(ReportStatus.PENDING)
                .build();
        when(reportRepository.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        when(adminService.cancelOrder(ORDER_ID, "Xac minh hang gia, huy don", ADMIN_ID))
                .thenReturn(Map.of("orderStatus", "CANCELLED", "productStatus", "AVAILABLE"));

        ReportResolveRequest request = new ReportResolveRequest(ReportAction.CANCEL_ORDER, "Xac minh hang gia, huy don");
        ReportResolveResponse response = reportService.resolveReport(adminUser, REPORT_ID, request);

        assertThat(response.status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(response.action()).isEqualTo(ReportAction.CANCEL_ORDER);
        assertThat(response.resolvedBy()).isEqualTo(ADMIN_ID);
        assertThat(response.resolvedAt()).isEqualTo(NOW);

        verify(adminService).cancelOrder(ORDER_ID, "Xac minh hang gia, huy don", ADMIN_ID);
        verify(auditService).log(ADMIN_ID, "REPORT_RESOLVE", "REPORT", REPORT_ID, "Xac minh hang gia, huy don");
        verify(notificationService).notify(eq(BUYER_ID), eq(NotificationType.REPORT_RESOLVED),
                contains("Chấp nhận"), anyString(), eq("REPORT"), eq(REPORT_ID));
    }

    @Test
    @DisplayName("UT-RPT-10: Bác bỏ — action = NONE -> Report REJECTED, không gọi AdminService thay đổi dữ liệu")
    void resolveReport_Reject_Success() {
        Report report = Report.builder()
                .id(REPORT_ID)
                .reporterId(BUYER_ID)
                .reportType(ReportType.COMPLAINT)
                .orderId(ORDER_ID)
                .status(ReportStatus.PENDING)
                .build();
        when(reportRepository.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportResolveRequest request = new ReportResolveRequest(ReportAction.NONE, "Khieu nai khong co co so hop ly");
        ReportResolveResponse response = reportService.resolveReport(adminUser, REPORT_ID, request);

        assertThat(response.status()).isEqualTo(ReportStatus.REJECTED);
        assertThat(response.action()).isEqualTo(ReportAction.NONE);
        assertThat(response.resolvedBy()).isEqualTo(ADMIN_ID);
        assertThat(response.resolvedAt()).isEqualTo(NOW);

        verifyNoInteractions(adminService);
        verify(auditService).log(ADMIN_ID, "REPORT_REJECT", "REPORT", REPORT_ID, "Khieu nai khong co co so hop ly");
        verify(notificationService).notify(eq(BUYER_ID), eq(NotificationType.REPORT_RESOLVED),
                contains("Bác bỏ"), anyString(), eq("REPORT"), eq(REPORT_ID));
    }

    @Test
    @DisplayName("UT-RPT-11: Xử lý lại báo cáo đã xử lý — Report RESOLVED -> 409 INVALID_STATUS_TRANSITION")
    void resolveReport_AlreadyResolved_Throws409() {
        Report report = Report.builder()
                .id(REPORT_ID)
                .reporterId(BUYER_ID)
                .reportType(ReportType.COMPLAINT)
                .orderId(ORDER_ID)
                .status(ReportStatus.RESOLVED)
                .build();
        when(reportRepository.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report));

        ReportResolveRequest request = new ReportResolveRequest(ReportAction.COMPLETE_ORDER, "Xu ly lai lan 2");
        assertThatThrownBy(() -> reportService.resolveReport(adminUser, REPORT_ID, request))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("INVALID_STATUS_TRANSITION") && ((AppException) e).getStatus() == HttpStatus.CONFLICT);

        verifyNoInteractions(adminService);
    }

    @Test
    @DisplayName("UT-RPT-12: HIDE_PRODUCT — VIOLATION trên PRODUCT có phiên ACTIVE -> Gọi AdminService.hideProduct trong cùng transaction; Report RESOLVED")
    void resolveReport_HideProduct_Success() {
        Report report = Report.builder()
                .id(REPORT_ID)
                .reporterId(OTHER_ID)
                .reportType(ReportType.VIOLATION)
                .reportedProductId(PRODUCT_ID)
                .status(ReportStatus.PENDING)
                .build();
        when(reportRepository.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        when(adminService.hideProduct(PRODUCT_ID, "Hang cam vi pham quy dinh", ADMIN_ID))
                .thenReturn(Map.of("productId", PRODUCT_ID, "productStatus", "HIDDEN"));

        ReportResolveRequest request = new ReportResolveRequest(ReportAction.HIDE_PRODUCT, "Hang cam vi pham quy dinh");
        ReportResolveResponse response = reportService.resolveReport(adminUser, REPORT_ID, request);

        assertThat(response.status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(response.action()).isEqualTo(ReportAction.HIDE_PRODUCT);
        verify(adminService).hideProduct(PRODUCT_ID, "Hang cam vi pham quy dinh", ADMIN_ID);
        verify(auditService).log(ADMIN_ID, "REPORT_RESOLVE", "REPORT", REPORT_ID, "Hang cam vi pham quy dinh");
    }

    @Test
    @DisplayName("UT-RPT-13: Action không hợp loại / không thực hiện được — COMPLETE_ORDER cho VIOLATION; BAN_USER khi không có reportedUserId; CANCEL_AUCTION khi không có phiên mở; note < 5")
    void resolveReport_InvalidActionOrMissingTarget_ThrowsErrors() {
        // Case 1: COMPLETE_ORDER cho VIOLATION -> 400
        Report violationReport = Report.builder()
                .id(REPORT_ID)
                .reporterId(OTHER_ID)
                .reportType(ReportType.VIOLATION)
                .reportedProductId(PRODUCT_ID)
                .status(ReportStatus.PENDING)
                .build();
        when(reportRepository.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(violationReport));

        ReportResolveRequest reqInvalidAction = new ReportResolveRequest(ReportAction.COMPLETE_ORDER, "Ghi chu hop le 123");
        assertThatThrownBy(() -> reportService.resolveReport(adminUser, REPORT_ID, reqInvalidAction))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("VALIDATION_ERROR") && ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST);

        // Case 2: BAN_USER khi không có reported_user_id -> 400
        ReportResolveRequest reqBanWithoutUser = new ReportResolveRequest(ReportAction.BAN_USER, "Ghi chu hop le 123");
        assertThatThrownBy(() -> reportService.resolveReport(adminUser, REPORT_ID, reqBanWithoutUser))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("VALIDATION_ERROR") && ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST);

        // Case 3: CANCEL_AUCTION khi sản phẩm không có phiên mở -> 409
        when(auctionRepository.findFirstByProductIdAndStatusIn(eq(PRODUCT_ID), anyCollection()))
                .thenReturn(Optional.empty());
        ReportResolveRequest reqCancelAuctionNoOpen = new ReportResolveRequest(ReportAction.CANCEL_AUCTION, "Ghi chu hop le 123");
        assertThatThrownBy(() -> reportService.resolveReport(adminUser, REPORT_ID, reqCancelAuctionNoOpen))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("INVALID_STATUS_TRANSITION") && ((AppException) e).getStatus() == HttpStatus.CONFLICT);

        // Case 4: note 4 ký tự -> 400
        ReportResolveRequest reqShortNote = new ReportResolveRequest(ReportAction.HIDE_PRODUCT, "1234");
        assertThatThrownBy(() -> reportService.resolveReport(adminUser, REPORT_ID, reqShortNote))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("VALIDATION_ERROR") && ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("UT-RPT-14: Hành động con lỗi ⇒ báo cáo không đổi — AdminService.hideProduct ném lỗi, lỗi lan ra, Report vẫn PENDING")
    void resolveReport_SubActionThrowsException_ReportRemainsPending() {
        Report report = Report.builder()
                .id(REPORT_ID)
                .reporterId(OTHER_ID)
                .reportType(ReportType.VIOLATION)
                .reportedProductId(PRODUCT_ID)
                .status(ReportStatus.PENDING)
                .build();
        when(reportRepository.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report));

        when(adminService.hideProduct(PRODUCT_ID, "Loi bat ngo xay ra", ADMIN_ID))
                .thenThrow(new AppException("INTERNAL_ERROR", "DB connection lost", HttpStatus.INTERNAL_SERVER_ERROR));

        ReportResolveRequest request = new ReportResolveRequest(ReportAction.HIDE_PRODUCT, "Loi bat ngo xay ra");
        assertThatThrownBy(() -> reportService.resolveReport(adminUser, REPORT_ID, request))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("INTERNAL_ERROR"));

        // Report must NOT be saved as RESOLVED
        verify(reportRepository, never()).save(any(Report.class));
        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING);
    }
}
