package com.omnibid.service;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.PageResponse;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.order.OrderConfirmReceiptResponse;
import com.omnibid.dto.order.OrderDetailResponse;
import com.omnibid.dto.order.OrderResponse;
import com.omnibid.dto.order.OrderShipResponse;
import com.omnibid.dto.order.ShippingInfoRequest;
import com.omnibid.entity.*;
import com.omnibid.enums.*;
import com.omnibid.repository.*;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.OrderAccessPolicy;
import com.omnibid.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private NotificationService notificationService;

    private OrderAccessPolicy orderAccessPolicy;
    private OrderLifecycleSupport orderLifecycleSupport;
    private OrderServiceImpl orderService;

    private final CurrentUser buyerUser = new CurrentUser(2L, false);
    private final CurrentUser sellerUser = new CurrentUser(1L, false);
    private final CurrentUser otherUser = new CurrentUser(99L, false);
    private final CurrentUser adminUser = new CurrentUser(10L, true);

    private Order sampleOrder;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        orderAccessPolicy = new OrderAccessPolicy();
        orderLifecycleSupport = new OrderLifecycleSupport(orderRepository, productRepository, paymentRepository, notificationService, reportRepository);

        AppProperties appProperties = new AppProperties();
        orderService = new OrderServiceImpl(
                orderRepository,
                productRepository,
                productImageRepository,
                userRepository,
                paymentRepository,
                reportRepository,
                orderAccessPolicy,
                orderLifecycleSupport,
                appProperties,
                clock
        );

        sampleOrder = Order.builder()
                .id(9L)
                .auctionId(12L)
                .productId(4L)
                .sellerId(1L)
                .buyerId(2L)
                .finalPrice(BigDecimal.valueOf(520000))
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentDeadline(NOW.plus(48, ChronoUnit.HOURS))
                .createdAt(NOW)
                .build();

        sampleProduct = Product.builder()
                .id(4L)
                .sellerId(1L)
                .title("Máy ảnh Fuji X100V")
                .status(ProductStatus.RESERVED)
                .build();
    }

    @Test
    @DisplayName("UT-ORD-01 Danh sách đơn của buyer")
    void getOrders_BuyerRole_ReturnsBuyerOrders() {
        Order buyOrder1 = Order.builder().id(1L).buyerId(2L).sellerId(3L).productId(4L).createdAt(NOW).build();
        Order buyOrder2 = Order.builder().id(2L).buyerId(2L).sellerId(3L).productId(4L).createdAt(NOW.minus(1, ChronoUnit.HOURS)).build();

        PageImpl<Order> page = new PageImpl<>(List.of(buyOrder1, buyOrder2));
        when(orderRepository.findByBuyerId(eq(2L), any(), any())).thenReturn(page);
        when(productRepository.findById(4L)).thenReturn(Optional.of(sampleProduct));

        PageResponse<OrderResponse> result = orderService.getOrders(buyerUser, "buyer", null, PageRequest.of(0, 20));

        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
    }

    @Test
    @DisplayName("UT-ORD-02 Admin gọi getOrders của user bị 403 FORBIDDEN")
    void getOrders_AdminUser_ThrowsForbidden() {
        assertThatThrownBy(() -> orderService.getOrders(adminUser, "buyer", null, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("UT-ORD-05 Cập nhật địa chỉ hợp lệ: PENDING_PAYMENT hoặc PAID")
    void updateShippingAddress_ValidStatus_Success() {
        ShippingInfoRequest request = new ShippingInfoRequest("12 Trần Hưng Đạo, Hà Nội", "0900000000");

        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(productRepository.findById(4L)).thenReturn(Optional.of(sampleProduct));

        OrderDetailResponse res = orderService.updateShippingInfo(buyerUser, 9L, request);
        assertNotNull(res);
        assertEquals("12 Trần Hưng Đạo, Hà Nội", sampleOrder.getShippingAddress());
        assertEquals("0900000000", sampleOrder.getPhoneNumber());
    }

    @Test
    @DisplayName("UT-ORD-06 Cập nhật địa chỉ sai người gọi: SELLER -> 403 FORBIDDEN")
    void updateShippingAddress_Seller_ThrowsForbidden() {
        ShippingInfoRequest request = new ShippingInfoRequest("12 Trần Hưng Đạo", "0900000000");
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));

        assertThatThrownBy(() -> orderService.updateShippingInfo(sellerUser, 9L, request))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("UT-ORD-07 Gửi hàng: POST /orders/{id}/ship thành công chuyển sang SHIPPING")
    void shipOrder_Success() {
        sampleOrder.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderShipResponse res = orderService.shipOrder(sellerUser, 9L, new com.omnibid.dto.order.ShipOrderRequest("TRK-12345"));
        assertNotNull(res);
        assertEquals(OrderStatus.SHIPPING, sampleOrder.getStatus());
        assertEquals(NOW, sampleOrder.getShippedAt());
        assertEquals("TRK-12345", sampleOrder.getTrackingCode());
        assertEquals(NOW.plus(7, ChronoUnit.DAYS), sampleOrder.getConfirmDeadline());
    }

    @Test
    @DisplayName("UT-ORD-08 Gửi hàng: không phải seller bị 403 FORBIDDEN")
    void shipOrder_NotSeller_ThrowsForbidden() {
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));

        assertThatThrownBy(() -> orderService.shipOrder(buyerUser, 9L, new com.omnibid.dto.order.ShipOrderRequest(null)))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("UT-ORD-12 Xác nhận nhận hàng: ORDER SHIPPING -> COMPLETED, Product SOLD")
    void confirmReceipt_Success() {
        sampleOrder.setStatus(OrderStatus.SHIPPING);
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));
        when(productRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        OrderConfirmReceiptResponse res = orderService.confirmReceipt(buyerUser, 9L);

        assertEquals(OrderStatus.COMPLETED, res.getOrderStatus());
        assertEquals(OrderStatus.COMPLETED, sampleOrder.getStatus());
        assertEquals(ProductStatus.SOLD, sampleProduct.getStatus());
    }

    @Test
    @DisplayName("UT-ORD-13 Xác nhận nhận hàng: không phải buyer bị 403 FORBIDDEN")
    void confirmReceipt_NotBuyer_ThrowsForbidden() {
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));

        assertThatThrownBy(() -> orderService.confirmReceipt(sellerUser, 9L))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.FORBIDDEN);
    }
}
