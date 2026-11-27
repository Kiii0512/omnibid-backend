package com.omnibid.service;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.PageResponse;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.order.OrderConfirmReceiptResponse;
import com.omnibid.dto.order.OrderDetailResponse;
import com.omnibid.dto.order.OrderSummaryResponse;
import com.omnibid.dto.order.ShipOrderRequest;
import com.omnibid.dto.order.ShipOrderResponse;
import com.omnibid.dto.order.ShippingInfoRequest;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.PaymentStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.PaymentRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
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
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");
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
        orderLifecycleSupport = new OrderLifecycleSupport(
                orderRepository, productRepository, paymentRepository, notificationService
        );

        AppProperties appProperties = new AppProperties();
        orderService = new OrderServiceImpl(
                orderRepository,
                productRepository,
                productImageRepository,
                userRepository,
                orderAccessPolicy,
                orderLifecycleSupport,
                appProperties,
                clock,
                notificationService
        );

        sampleOrder = Order.builder()
                .id(9L)
                .auctionId(12L)
                .productId(4L)
                .sellerId(1L)
                .buyerId(2L)
                .finalPrice(BigDecimal.valueOf(520000))
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentDeadline(NOW.plus(Duration.ofHours(48)))
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
    @DisplayName("GET /orders: lọc theo BUYER, SELLER hoặc cả hai; role/sort sai trả 400 VALIDATION_ERROR; không có N+1")
    void getOrders_RoleAndSortValidation() {
        when(orderRepository.findByBuyerId(eq(2L), any(), any())).thenReturn(new PageImpl<>(List.of(sampleOrder)));
        when(productRepository.findAllById(any())).thenReturn(List.of(sampleProduct));
        when(productImageRepository.findByProductIdInOrderByIdAsc(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(
                User.builder().id(1L).fullName("Seller A").build(),
                User.builder().id(2L).fullName("Buyer B").build()
        ));

        PageResponse<OrderSummaryResponse> buyerRes = orderService.getOrders(
                buyerUser, "BUYER", null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        assertEquals(1, buyerRes.getTotalElements());
        assertEquals("Máy ảnh Fuji X100V", buyerRes.getContent().get(0).getTitle());
        assertEquals("Buyer B", buyerRes.getContent().get(0).getBuyer().fullName());
        assertEquals("Seller A", buyerRes.getContent().get(0).getSeller().fullName());
        verify(productRepository, never()).findById(any());
        verify(userRepository, never()).findById(any());

        when(orderRepository.findBySellerId(eq(1L), any(), any())).thenReturn(new PageImpl<>(List.of(sampleOrder)));
        PageResponse<OrderSummaryResponse> sellerRes = orderService.getOrders(
                sellerUser, "SELLER", null, PageRequest.of(0, 10)
        );
        assertEquals(1, sellerRes.getTotalElements());

        when(orderRepository.findByBuyerOrSeller(eq(2L), any(), any())).thenReturn(new PageImpl<>(List.of(sampleOrder)));
        PageResponse<OrderSummaryResponse> bothRes = orderService.getOrders(
                buyerUser, null, null, PageRequest.of(0, 10)
        );
        assertEquals(1, bothRes.getTotalElements());

        BusinessException badRole = assertThrows(BusinessException.class,
                () -> orderService.getOrders(buyerUser, "ADMIN", null, PageRequest.of(0, 10)));
        assertEquals(ErrorCode.VALIDATION_ERROR, badRole.getCode());

        BusinessException badSort = assertThrows(BusinessException.class,
                () -> orderService.getOrders(buyerUser, null, null, PageRequest.of(0, 10, Sort.by("unknownField"))));
        assertEquals(ErrorCode.VALIDATION_ERROR, badSort.getCode());
    }

    @Test
    @DisplayName("GET /orders/{id}: chỉ buyer hoặc seller được xem; other và ADMIN bị 403 FORBIDDEN")
    void getOrderById_AccessControl() {
        when(orderRepository.findById(9L)).thenReturn(Optional.of(sampleOrder));
        when(productRepository.findById(4L)).thenReturn(Optional.of(sampleProduct));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).fullName("Seller A").build()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).fullName("Buyer B").build()));

        OrderDetailResponse buyerView = orderService.getOrderById(buyerUser, 9L);
        assertEquals("BUYER", buyerView.getViewerRole());
        assertEquals(ProductStatus.RESERVED, buyerView.getProductStatus());

        OrderDetailResponse sellerView = orderService.getOrderById(sellerUser, 9L);
        assertEquals("SELLER", sellerView.getViewerRole());

        BusinessException otherEx = assertThrows(BusinessException.class, () -> orderService.getOrderById(otherUser, 9L));
        assertEquals(ErrorCode.FORBIDDEN, otherEx.getCode());

        BusinessException adminEx = assertThrows(BusinessException.class, () -> orderService.getOrderById(adminUser, 9L));
        assertEquals(ErrorCode.FORBIDDEN, adminEx.getCode());
    }

    @Test
    @DisplayName("PATCH /orders/{id}/shipping-info: chỉ buyer khi PENDING_PAYMENT; trạng thái khác trả INVALID_STATUS_TRANSITION")
    void updateShippingInfo_PendingPaymentOnly() {
        ShippingInfoRequest request = ShippingInfoRequest.builder()
                .shippingAddress("12 Trần Hưng Đạo, Hà Nội")
                .phoneNumber("0900000000")
                .build();

        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderDetailResponse res = orderService.updateShippingInfo(buyerUser, 9L, request);
        assertEquals("12 Trần Hưng Đạo, Hà Nội", res.getShippingAddress());
        assertEquals("0900000000", res.getPhoneNumber());

        BusinessException sellerEx = assertThrows(BusinessException.class,
                () -> orderService.updateShippingInfo(sellerUser, 9L, request));
        assertEquals(ErrorCode.FORBIDDEN, sellerEx.getCode());

        sampleOrder.setStatus(OrderStatus.PAID);
        BusinessException paidEx = assertThrows(BusinessException.class,
                () -> orderService.updateShippingInfo(buyerUser, 9L, request));
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, paidEx.getCode());
    }

    @Test
    @DisplayName("POST /orders/{id}/ship: chỉ seller khi PAID -> SHIPPING, đặt confirmDeadline và thông báo buyer")
    void shipOrder_SuccessAndGuards() {
        sampleOrder.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));

        ShipOrderResponse res = orderService.shipOrder(sellerUser, 9L, new ShipOrderRequest("VN123456"));

        assertEquals(9L, res.getId());
        assertEquals(OrderStatus.SHIPPING, res.getOrderStatus());
        assertEquals(NOW, res.getShippedAt());
        assertEquals(NOW.plus(Duration.ofDays(7)), res.getConfirmDeadline());
        assertEquals("VN123456", res.getTrackingCode());
        verify(orderRepository).save(sampleOrder);
        verify(notificationService).notify(eq(2L), eq(NotificationType.ORDER_UPDATE), any(), any(), eq("ORDER"), eq(9L));

        BusinessException buyerEx = assertThrows(BusinessException.class,
                () -> orderService.shipOrder(buyerUser, 9L, null));
        assertEquals(ErrorCode.FORBIDDEN, buyerEx.getCode());

        // Already SHIPPING -> INVALID_STATUS_TRANSITION
        BusinessException statusEx = assertThrows(BusinessException.class,
                () -> orderService.shipOrder(sellerUser, 9L, null));
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, statusEx.getCode());
    }

    @Test
    @DisplayName("POST /orders/{id}/confirm-receipt: buyer khi SHIPPING -> COMPLETED, product SOLD (giữ HIDDEN nếu bị ẩn)")
    void confirmReceipt_SuccessAndHiddenProduct() {
        sampleOrder.setStatus(OrderStatus.SHIPPING);
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));
        when(productRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(sampleProduct));

        OrderConfirmReceiptResponse res = orderService.confirmReceipt(buyerUser, 9L);

        assertEquals(OrderStatus.COMPLETED, res.getOrderStatus());
        assertEquals(ProductStatus.SOLD, res.getProductStatus());
        assertEquals(ProductStatus.SOLD, sampleProduct.getStatus());
        verify(notificationService).notify(eq(1L), eq(NotificationType.ORDER_UPDATE), any(), any(), eq("ORDER"), eq(9L));

        // Product HIDDEN stays HIDDEN (BR-16)
        Order hiddenOrder = Order.builder()
                .id(10L).productId(5L).sellerId(1L).buyerId(2L).status(OrderStatus.SHIPPING).build();
        Product hiddenProduct = Product.builder().id(5L).status(ProductStatus.HIDDEN).build();
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hiddenOrder));
        when(productRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(hiddenProduct));

        OrderConfirmReceiptResponse hiddenRes = orderService.confirmReceipt(buyerUser, 10L);
        assertEquals(OrderStatus.COMPLETED, hiddenRes.getOrderStatus());
        assertEquals(ProductStatus.HIDDEN, hiddenRes.getProductStatus());
    }

    @Test
    @DisplayName("POST /orders/{id}/confirm-receipt: sai vai trò hoặc sai trạng thái ném lỗi tương ứng")
    void confirmReceipt_InvalidContext() {
        sampleOrder.setStatus(OrderStatus.SHIPPING);
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));

        BusinessException sellerEx = assertThrows(BusinessException.class,
                () -> orderService.confirmReceipt(sellerUser, 9L));
        assertEquals(ErrorCode.FORBIDDEN, sellerEx.getCode());

        sampleOrder.setStatus(OrderStatus.PAID);
        BusinessException paidEx = assertThrows(BusinessException.class,
                () -> orderService.confirmReceipt(buyerUser, 9L));
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, paidEx.getCode());
    }

    @Test
    @DisplayName("expireOverdueOrders: quá hạn chưa có SUCCESS -> EXPIRED, product release (trừ HIDDEN), thông báo 2 bên")
    void expireOverdueOrders_SuccessAndGuards() {
        sampleOrder.setPaymentDeadline(NOW);
        when(orderRepository.findIdsByStatusAndPaymentDeadlineLessThanEqual(eq(OrderStatus.PENDING_PAYMENT), eq(NOW), any()))
                .thenReturn(List.of(9L));
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.existsByOrderIdAndStatus(9L, PaymentStatus.SUCCESS)).thenReturn(false);
        when(productRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(sampleProduct));

        orderService.expireOverdueOrders();

        assertEquals(OrderStatus.EXPIRED, sampleOrder.getStatus());
        assertEquals(ProductStatus.AVAILABLE, sampleProduct.getStatus());
        verify(notificationService).notify(eq(2L), eq(NotificationType.ORDER_UPDATE), any(), any(), eq("ORDER"), eq(9L));
        verify(notificationService).notify(eq(1L), eq(NotificationType.ORDER_UPDATE), any(), any(), eq("ORDER"), eq(9L));

        // Product HIDDEN stays HIDDEN
        Order hiddenOrder = Order.builder()
                .id(11L).productId(5L).sellerId(1L).buyerId(2L)
                .status(OrderStatus.PENDING_PAYMENT).paymentDeadline(NOW).build();
        Product hiddenProduct = Product.builder().id(5L).status(ProductStatus.HIDDEN).build();
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(hiddenOrder));
        when(paymentRepository.existsByOrderIdAndStatus(11L, PaymentStatus.SUCCESS)).thenReturn(false);
        when(productRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(hiddenProduct));

        orderLifecycleSupport.expireOne(11L, NOW);
        assertEquals(OrderStatus.EXPIRED, hiddenOrder.getStatus());
        assertEquals(ProductStatus.HIDDEN, hiddenProduct.getStatus());
    }

    @Test
    @DisplayName("autoCompleteOrders: quá confirm_deadline -> COMPLETED, product SOLD (trừ HIDDEN), thông báo 2 bên")
    void autoCompleteOrders_Success() {
        sampleOrder.setStatus(OrderStatus.SHIPPING);
        sampleOrder.setConfirmDeadline(NOW);
        when(orderRepository.findIdsByStatusAndConfirmDeadlineLessThanEqual(eq(OrderStatus.SHIPPING), eq(NOW), any()))
                .thenReturn(List.of(9L));
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));
        when(productRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(sampleProduct));

        orderService.autoCompleteOrders();

        assertEquals(OrderStatus.COMPLETED, sampleOrder.getStatus());
        assertEquals(ProductStatus.SOLD, sampleProduct.getStatus());
        verify(notificationService).notify(eq(2L), eq(NotificationType.ORDER_UPDATE), any(), any(), eq("ORDER"), eq(9L));
        verify(notificationService).notify(eq(1L), eq(NotificationType.ORDER_UPDATE), any(), any(), eq("ORDER"), eq(9L));
    }

    @Test
    @DisplayName("Chuyển trạng thái cấm trên entity Order ném BusinessException INVALID_STATUS_TRANSITION")
    void entityForbiddenTransitions() {
        Order order = Order.builder().status(OrderStatus.EXPIRED).build();
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION,
                assertThrows(BusinessException.class, () -> order.markPaid(NOW)).getCode());

        order.setStatus(OrderStatus.PAID);
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION,
                assertThrows(BusinessException.class, () -> order.expire(NOW)).getCode());

        order.setStatus(OrderStatus.COMPLETED);
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION,
                assertThrows(BusinessException.class, () -> order.cancel("Reason", NOW)).getCode());
    }
}
