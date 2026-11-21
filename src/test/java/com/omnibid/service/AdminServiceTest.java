package com.omnibid.service;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.dto.admin.*;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.order.OrderResponse;
import com.omnibid.entity.*;
import com.omnibid.enums.*;
import com.omnibid.repository.*;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.impl.AdminNotificationHelper;
import com.omnibid.service.impl.AdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Long ADMIN_ID = 9L;
    private static final Long USER_ID = 3L;
    private static final Long AUCTION_ID = 12L;
    private static final Long ORDER_ID = 9L;
    private static final Long PRODUCT_ID = 10L;

    private final CurrentUser adminUser = new CurrentUser(ADMIN_ID, true);
    private final CurrentUser normalUser = new CurrentUser(USER_ID, false);

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AdminNotificationHelper notificationHelper;

    private AdminServiceImpl adminService;

    private User sampleUser;
    private User adminEntity;
    private Product sampleProduct;
    private Auction sampleAuction;
    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        adminService = new AdminServiceImpl(
                productRepository,
                productImageRepository,
                orderRepository,
                paymentRepository,
                auctionRepository,
                userRepository,
                reportRepository,
                categoryRepository,
                notificationHelper,
                clock
        );

        sampleUser = User.builder()
                .id(USER_ID)
                .email("user@test.com")
                .fullName("User Three")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        adminEntity = User.builder()
                .id(ADMIN_ID)
                .email("admin@test.com")
                .fullName("Admin")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        sampleProduct = Product.builder()
                .id(PRODUCT_ID)
                .sellerId(USER_ID)
                .title("Sản phẩm mẫu")
                .status(ProductStatus.AVAILABLE)
                .build();

        sampleAuction = Auction.builder()
                .id(AUCTION_ID)
                .productId(PRODUCT_ID)
                .sellerId(USER_ID)
                .status(AuctionStatus.ACTIVE)
                .build();

        sampleOrder = Order.builder()
                .id(ORDER_ID)
                .productId(PRODUCT_ID)
                .buyerId(USER_ID)
                .sellerId(1L)
                .status(OrderStatus.PENDING_PAYMENT)
                .build();
    }

    @Test
    @DisplayName("banUser — Admin khóa tài khoản: chuyển BANNED, hủy phiên, hủy đơn, giải phóng product")
    void banUser_Success() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(sampleUser));
        when(auctionRepository.findBySellerIdAndStatusIn(eq(USER_ID), any())).thenReturn(List.of(sampleAuction));
        when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(sampleAuction));
        when(orderRepository.findUnpaidOrdersByUser(USER_ID)).thenReturn(List.of(sampleOrder));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(sampleProduct));

        AdminBanUserResponse res = adminService.banUser(adminUser, USER_ID, "Vi phạm quy chế");

        assertNotNull(res);
        assertEquals(UserStatus.BANNED, sampleUser.getStatus());
        assertEquals(AuctionStatus.CANCELLED, sampleAuction.getStatus());
        assertEquals(OrderStatus.CANCELLED, sampleOrder.getStatus());
        assertEquals(1, res.cancelledAuctions());
        assertEquals(1, res.cancelledOrders());
        verify(notificationHelper).dispatchBanNotifications(any(), any(), eq(NOW));
    }

    @Test
    @DisplayName("banUser — Không thể khóa tài khoản ADMIN bị CANNOT_BAN_ADMIN")
    void banUser_AdminUser_ThrowsCannotBanAdmin() {
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminEntity));

        assertThatThrownBy(() -> adminService.banUser(adminUser, ADMIN_ID, "Khóa admin"))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.CANNOT_BAN_ADMIN);
    }

    @Test
    @DisplayName("unbanUser — Mở khóa tài khoản: chuyển ACTIVE")
    void unbanUser_Success() {
        sampleUser.setStatus(UserStatus.BANNED);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(sampleUser));

        AdminUserResponse res = adminService.unbanUser(adminUser, USER_ID, "Đã khiếu nại thành công");

        assertNotNull(res);
        assertEquals(UserStatus.ACTIVE, sampleUser.getStatus());
    }

    @Test
    @DisplayName("hideProduct — Ẩn sản phẩm: chuyển HIDDEN, hủy phiên mở, hủy đơn chưa thanh toán")
    void hideProduct_Success() {
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(sampleProduct));
        when(auctionRepository.findByProductIdAndStatusIn(eq(PRODUCT_ID), any())).thenReturn(List.of(sampleAuction));
        when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(sampleAuction));
        when(orderRepository.findByProductIdAndStatusIn(eq(PRODUCT_ID), any())).thenReturn(List.of(sampleOrder));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));

        ProductHideResponse res = adminService.hideProduct(adminUser, PRODUCT_ID, "Hàng cấm");

        assertNotNull(res);
        assertEquals(ProductStatus.HIDDEN, sampleProduct.getStatus());
        assertEquals(AuctionStatus.CANCELLED, sampleAuction.getStatus());
        assertEquals(OrderStatus.CANCELLED, sampleOrder.getStatus());
        verify(notificationHelper).dispatchHideProductNotifications(any(), any(), eq(NOW));
    }

    @Test
    @DisplayName("cancelAuction — Admin hủy phiên đang mở thành công")
    void cancelAuction_Success() {
        when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(sampleProduct));

        AuctionResponse res = adminService.cancelAuction(adminUser, AUCTION_ID, "Lý do hủy");

        assertNotNull(res);
        assertEquals(AuctionStatus.CANCELLED, sampleAuction.getStatus());
        assertEquals("Lý do hủy", sampleAuction.getCancelReason());
        verify(notificationHelper).notifyAuctionCancelled(eq(AUCTION_ID), any(), any(), eq("Lý do hủy"), eq(NOW));
    }

    @Test
    @DisplayName("cancelOrder — Admin hủy đơn PENDING_PAYMENT thành công")
    void cancelOrder_Success() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(sampleProduct));

        OrderResponse res = adminService.cancelOrder(adminUser, ORDER_ID, "Hủy đơn");

        assertNotNull(res);
        assertEquals(OrderStatus.CANCELLED, sampleOrder.getStatus());
        verify(notificationHelper).notifyOrderCancelled(eq(ORDER_ID), eq(USER_ID), eq("Hủy đơn"));
    }

    @Test
    @DisplayName("cancelOrder — Hủy đơn không phải PENDING_PAYMENT bị INVALID_STATUS_TRANSITION")
    void cancelOrder_NotPendingPayment_ThrowsConflict() {
        sampleOrder.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));

        assertThatThrownBy(() -> adminService.cancelOrder(adminUser, ORDER_ID, "Hủy đơn đã trả tiền"))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("getStatistics — Thống kê hệ thống")
    void getStatistics_Success() {
        when(userRepository.count()).thenReturn(100L);
        when(orderRepository.sumTotalCompletedRevenue()).thenReturn(BigDecimal.valueOf(50000000));
        when(reportRepository.countByStatus(ReportStatus.PENDING)).thenReturn(2L);

        AdminStatisticsResponse res = adminService.getStatistics(adminUser);

        assertNotNull(res);
        assertEquals(100L, res.totalUsers());
        assertEquals(BigDecimal.valueOf(50000000), res.completedOrderValue());
        assertEquals(2L, res.pendingReports());
    }

    @Test
    @DisplayName("Normal user gọi adminService bị 403 FORBIDDEN")
    void nonAdmin_ThrowsForbidden() {
        assertThatThrownBy(() -> adminService.getStatistics(normalUser))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.FORBIDDEN);
    }
}
