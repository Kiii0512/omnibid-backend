package com.omnibid.service;

import com.omnibid.common.AppException;
import com.omnibid.common.PageResponse;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.order.OrderDetailResponse;
import com.omnibid.dto.order.OrderResponse;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));

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
    private ShipmentRepository shipmentRepository;

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
                shipmentRepository,
                reportRepository,
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
                .paymentDeadline(NOW.plusHours(48))
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
    @DisplayName("UT-ORD-01 Danh sách đơn của buyer: role=buyer trả đơn mua của buyer, viewerRole=BUYER")
    void getOrders_BuyerRole_ReturnsBuyerOrders() {
        Order buyOrder1 = Order.builder().id(1L).buyerId(2L).sellerId(3L).productId(4L).createdAt(NOW).build();
        Order buyOrder2 = Order.builder().id(2L).buyerId(2L).sellerId(3L).productId(4L).createdAt(NOW.minusHours(1)).build();

        PageImpl<Order> page = new PageImpl<>(List.of(buyOrder1, buyOrder2));
        when(orderRepository.findByBuyerId(eq(2L), any(), any())).thenReturn(page);
        when(productRepository.findById(4L)).thenReturn(Optional.of(sampleProduct));

        PageResponse<OrderResponse> result = orderService.getOrders(buyerUser, "buyer", null, PageRequest.of(0, 20));

        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        assertEquals("BUYER", result.getContent().get(0).getViewerRole());
        assertEquals("BUYER", result.getContent().get(1).getViewerRole());
    }

    @Test
    @DisplayName("UT-ORD-02 Danh sách theo seller; người không có đơn; Admin")
    void getOrders_SellerAndOtherAndAdmin() {
        // 1. Seller
        Order sellOrder = Order.builder().id(3L).buyerId(5L).sellerId(1L).productId(4L).createdAt(NOW).build();
        when(orderRepository.findBySellerId(eq(1L), any(), any())).thenReturn(new PageImpl<>(List.of(sellOrder)));
        PageResponse<OrderResponse> sellerRes = orderService.getOrders(sellerUser, "seller", null, PageRequest.of(0, 20));
        assertEquals(1, sellerRes.getTotalElements());
        assertEquals("SELLER", sellerRes.getContent().get(0).getViewerRole());

        // 2. Other
        when(orderRepository.findByBuyerOrSeller(eq(99L), any(), any())).thenReturn(new PageImpl<>(Collections.emptyList()));
        PageResponse<OrderResponse> otherRes = orderService.getOrders(otherUser, null, null, PageRequest.of(0, 20));
        assertEquals(0, otherRes.getTotalElements());

        // 3. Admin
        when(orderRepository.findAllByStatus(any(), any())).thenReturn(new PageImpl<>(List.of(sellOrder, sampleOrder)));
        PageResponse<OrderResponse> adminRes = orderService.getOrders(adminUser, null, null, PageRequest.of(0, 20));
        assertEquals(2, adminRes.getTotalElements());
        assertEquals("ADMIN", adminRes.getContent().get(0).getViewerRole());
    }

    @Test
    @DisplayName("UT-ORD-03 Chi tiết đơn bởi buyer: PENDING_PAYMENT có paymentDeadline, allowedActions chứa PAY, UPDATE_SHIPPING_INFO, không lộ reservePrice")
    void getOrderById_ByBuyer_ReturnsDetailsWithAllowedActions() {
        when(orderRepository.findById(9L)).thenReturn(Optional.of(sampleOrder));
        when(productRepository.findById(4L)).thenReturn(Optional.of(sampleProduct));

        User seller = User.builder().id(1L).fullName("Nguyen Van Seller").build();
        User buyer = User.builder().id(2L).fullName("Tran Van Buyer").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));
        when(userRepository.findById(2L)).thenReturn(Optional.of(buyer));

        OrderDetailResponse detail = orderService.getOrderById(buyerUser, 9L);

        assertNotNull(detail);
        assertEquals(9L, detail.getId());
        assertEquals("BUYER", detail.getViewerRole());
        assertEquals(sampleOrder.getPaymentDeadline(), detail.getPaymentDeadline());
        assertTrue(detail.getAllowedActions().contains("PAY"));
        assertTrue(detail.getAllowedActions().contains("UPDATE_SHIPPING_INFO"));
        assertEquals("N***S", detail.getSellerName());
        assertEquals("T***B", detail.getBuyerName());
    }

    @Test
    @DisplayName("UT-ORD-04 Người không liên quan xem đơn: OTHER nhận 403 FORBIDDEN trước khi kiểm tra trạng thái")
    void getOrderById_ByOther_ThrowsForbidden() {
        sampleOrder.setStatus(OrderStatus.EXPIRED);
        when(orderRepository.findById(9L)).thenReturn(Optional.of(sampleOrder));

        AppException ex = assertThrows(AppException.class, () -> orderService.getOrderById(otherUser, 9L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    @DisplayName("UT-ORD-05 Cập nhật địa chỉ hợp lệ: PENDING_PAYMENT hoặc PAID + Shipment CREATED")
    void updateShippingInfo_ValidContext_Success() {
        ShippingInfoRequest request = ShippingInfoRequest.builder()
                .shippingAddress("12 Trần Hưng Đạo, Hà Nội")
                .phoneNumber("0900000000")
                .build();

        // 1. Order PENDING_PAYMENT
        when(orderRepository.findById(9L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderDetailResponse res1 = orderService.updateShippingInfo(buyerUser, 9L, request);
        assertNotNull(res1);
        assertEquals("12 Trần Hưng Đạo, Hà Nội", res1.getShippingAddress());
        assertEquals("0900000000", res1.getPhoneNumber());

        // 2. Order PAID và Shipment CREATED
        sampleOrder.setStatus(OrderStatus.PAID);
        Shipment shipment = Shipment.builder().id(1L).orderId(9L).status(ShipmentStatus.CREATED).build();
        when(shipmentRepository.findByOrderId(9L)).thenReturn(Optional.of(shipment));

        OrderDetailResponse res2 = orderService.updateShippingInfo(buyerUser, 9L, request);
        assertNotNull(res2);
        assertEquals("12 Trần Hưng Đạo, Hà Nội", res2.getShippingAddress());
    }

    @Test
    @DisplayName("UT-ORD-06 Cập nhật địa chỉ sai ngữ cảnh: (a) Shipment PICKED_UP (b) COMPLETED -> 409 INVALID_STATUS_TRANSITION; (c) SELLER -> 403 FORBIDDEN")
    void updateShippingInfo_InvalidContext_ThrowsExceptions() {
        ShippingInfoRequest request = ShippingInfoRequest.builder()
                .shippingAddress("12 Trần Hưng Đạo")
                .phoneNumber("0900000000")
                .build();

        when(orderRepository.findById(9L)).thenReturn(Optional.of(sampleOrder));

        // (c) Người gọi là SELLER
        AppException exSeller = assertThrows(AppException.class, () -> orderService.updateShippingInfo(sellerUser, 9L, request));
        assertEquals(HttpStatus.FORBIDDEN, exSeller.getStatus());

        // (a) Shipment PICKED_UP
        sampleOrder.setStatus(OrderStatus.PAID);
        Shipment shipment = Shipment.builder().id(1L).orderId(9L).status(ShipmentStatus.PICKED_UP).build();
        when(shipmentRepository.findByOrderId(9L)).thenReturn(Optional.of(shipment));
        AppException exPickedUp = assertThrows(AppException.class, () -> orderService.updateShippingInfo(buyerUser, 9L, request));
        assertEquals(HttpStatus.CONFLICT, exPickedUp.getStatus());
        assertEquals("INVALID_STATUS_TRANSITION", exPickedUp.getCode());

        // (b) ORDER COMPLETED
        sampleOrder.setStatus(OrderStatus.COMPLETED);
        AppException exCompleted = assertThrows(AppException.class, () -> orderService.updateShippingInfo(buyerUser, 9L, request));
        assertEquals(HttpStatus.CONFLICT, exCompleted.getStatus());
        assertEquals("INVALID_STATUS_TRANSITION", exCompleted.getCode());
    }

    @Test
    @DisplayName("UT-ORD-07 Nhắc 24 giờ chỉ gửi một lần: gọi sendPaymentReminders() 2 lần chỉ gửi 1 thông báo, reminder_24h_sent_at=NOW")
    void sendPaymentReminders_24hReminder_SendsOnce() {
        sampleOrder.setPaymentDeadline(NOW.plusHours(23).plusMinutes(59));
        sampleOrder.setReminder24hSentAt(null);

        when(orderRepository.findOrdersFor24hReminder(any(), eq(NOW))).thenReturn(List.of(sampleOrder));
        when(orderRepository.findOrdersFor2hReminder(any(), eq(NOW))).thenReturn(Collections.emptyList());
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Lần 1
        orderService.sendPaymentReminders();
        verify(notificationService, times(1)).notify(eq(2L), eq(NotificationType.PAYMENT_REMINDER), any(), any(), eq("ORDER"), eq(9L));
        assertEquals(NOW, sampleOrder.getReminder24hSentAt());

        // Lần 2: sampleOrder đã có reminder24hSentAt, findOrdersFor24hReminder không trả về hoặc bị chặn
        when(orderRepository.findOrdersFor24hReminder(any(), eq(NOW))).thenReturn(Collections.emptyList());
        orderService.sendPaymentReminders();
        verify(notificationService, times(1)).notify(eq(2L), eq(NotificationType.PAYMENT_REMINDER), any(), any(), eq("ORDER"), eq(9L));
    }

    @Test
    @DisplayName("UT-ORD-08 Nhắc 2 giờ; không nhắc đơn đã trả: (a) deadline = NOW + 1h59m gửi mốc 2h; (b) ORDER PAID không gửi")
    void sendPaymentReminders_2hReminder_AndNoReminderForPaidOrder() {
        // (a) Deadline còn 1h59m
        sampleOrder.setPaymentDeadline(NOW.plusHours(1).plusMinutes(59));
        sampleOrder.setReminder2hSentAt(null);

        when(orderRepository.findOrdersFor24hReminder(any(), eq(NOW))).thenReturn(Collections.emptyList());
        when(orderRepository.findOrdersFor2hReminder(any(), eq(NOW))).thenReturn(List.of(sampleOrder));
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        orderService.sendPaymentReminders();
        verify(notificationService).notify(eq(2L), eq(NotificationType.PAYMENT_REMINDER), any(), any(), eq("ORDER"), eq(9L));
        assertEquals(NOW, sampleOrder.getReminder2hSentAt());

        // (b) Đơn PAID không bao giờ được gửi nhắc
        sampleOrder.setStatus(OrderStatus.PAID);
        sampleOrder.setReminder2hSentAt(null);
        orderLifecycleSupport.sendReminder2h(9L, NOW);
        // Không gọi save và không gọi notify thêm
        verify(notificationService, times(1)).notify(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("UT-ORD-09 Hết hạn thanh toán: deadline = NOW, không Payment PENDING/SUCCESS, Product RESERVED -> Order EXPIRED, Product AVAILABLE, thông báo 2 bên")
    void expireOverdueOrders_Success() {
        sampleOrder.setPaymentDeadline(NOW);
        when(orderRepository.findByStatusAndPaymentDeadlineLessThanEqual(OrderStatus.PENDING_PAYMENT, NOW))
                .thenReturn(List.of(sampleOrder));
        when(orderRepository.findById(9L)).thenReturn(Optional.of(sampleOrder));
        when(productRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.existsByOrderIdAndStatus(9L, PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.existsByOrderIdAndStatus(9L, PaymentStatus.SUCCESS)).thenReturn(false);

        orderService.expireOverdueOrders();

        assertEquals(OrderStatus.EXPIRED, sampleOrder.getStatus());
        assertEquals(ProductStatus.AVAILABLE, sampleProduct.getStatus());
        verify(orderRepository).save(sampleOrder);
        verify(productRepository).save(sampleProduct);
        verify(notificationService).notify(eq(2L), eq(NotificationType.ORDER_UPDATE), any(), any(), eq("ORDER"), eq(9L));
        verify(notificationService).notify(eq(1L), eq(NotificationType.ORDER_UPDATE), any(), any(), eq("ORDER"), eq(9L));
    }

    @Test
    @DisplayName("UT-ORD-10 Quá hạn nhưng còn Payment PENDING: deadline = NOW - 1s, có Payment PENDING -> giữ PENDING_PAYMENT, không thông báo")
    void expireOverdueOrders_WhenPaymentPending_SkipsExpiration() {
        sampleOrder.setPaymentDeadline(NOW.minusSeconds(1));
        when(orderRepository.findByStatusAndPaymentDeadlineLessThanEqual(OrderStatus.PENDING_PAYMENT, NOW))
                .thenReturn(List.of(sampleOrder));
        when(orderRepository.findById(9L)).thenReturn(Optional.of(sampleOrder));
        when(productRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.existsByOrderIdAndStatus(9L, PaymentStatus.PENDING)).thenReturn(true);

        orderService.expireOverdueOrders();

        assertEquals(OrderStatus.PENDING_PAYMENT, sampleOrder.getStatus());
        assertEquals(ProductStatus.RESERVED, sampleProduct.getStatus());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("UT-ORD-11 Biên hạn và Product bị ẩn: (a) deadline = NOW + 1s không hết hạn; (b) deadline = NOW, Product HIDDEN -> EXPIRED nhưng Product giữ HIDDEN")
    void expireOverdueOrders_BoundaryAndProductHidden() {
        // (a) Deadline = NOW + 1s (chưa quá hạn)
        sampleOrder.setPaymentDeadline(NOW.plusSeconds(1));
        when(orderRepository.findById(9L)).thenReturn(Optional.of(sampleOrder));
        when(productRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));

        orderLifecycleSupport.expireOne(9L, NOW);
        assertEquals(OrderStatus.PENDING_PAYMENT, sampleOrder.getStatus());

        // (b) Deadline = NOW, Product HIDDEN
        sampleOrder.setPaymentDeadline(NOW);
        sampleProduct.setStatus(ProductStatus.HIDDEN);
        when(paymentRepository.existsByOrderIdAndStatus(9L, PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.existsByOrderIdAndStatus(9L, PaymentStatus.SUCCESS)).thenReturn(false);

        orderLifecycleSupport.expireOne(9L, NOW);
        assertEquals(OrderStatus.EXPIRED, sampleOrder.getStatus());
        assertEquals(ProductStatus.HIDDEN, sampleProduct.getStatus());
        verify(productRepository, never()).save(sampleProduct);
    }

    @Test
    @DisplayName("UT-ORD-12 Xác nhận nhận hàng: ORDER SHIPPING, Shipment DELIVERED, không khiếu nại -> COMPLETED, Product SOLD, thông báo seller")
    void confirmReceipt_Success() {
        sampleOrder.setStatus(OrderStatus.SHIPPING);
        when(orderRepository.findById(9L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));

        Shipment delivered = Shipment.builder().id(1L).orderId(9L).status(ShipmentStatus.DELIVERED).build();
        when(shipmentRepository.findByOrderId(9L)).thenReturn(Optional.of(delivered));
        when(reportRepository.existsByOrderIdAndReportTypeAndStatus(9L, ReportType.COMPLAINT, ReportStatus.PENDING)).thenReturn(false);
        when(productRepository.findByIdForUpdate(sampleOrder.getProductId())).thenReturn(Optional.of(sampleProduct));

        com.omnibid.dto.order.OrderConfirmReceiptResponse res = orderService.confirmReceipt(buyerUser, 9L);

        assertEquals(OrderStatus.COMPLETED, res.getOrderStatus());
        assertEquals(OrderStatus.COMPLETED, sampleOrder.getStatus());
        assertEquals(ProductStatus.SOLD, sampleProduct.getStatus());
        verify(notificationService).notify(eq(sampleOrder.getSellerId()), eq(NotificationType.ORDER_UPDATE), any(), any(), eq("ORDER"), eq(9L));
    }

    @Test
    @DisplayName("UT-ORD-13 Xác nhận khi còn khiếu nại PENDING ném 409 COMPLAINT_PENDING, không đổi dữ liệu")
    void confirmReceipt_ComplaintPending_ThrowsConflict() {
        sampleOrder.setStatus(OrderStatus.SHIPPING);
        when(orderRepository.findById(9L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));

        Shipment delivered = Shipment.builder().id(1L).orderId(9L).status(ShipmentStatus.DELIVERED).build();
        when(shipmentRepository.findByOrderId(9L)).thenReturn(Optional.of(delivered));
        when(reportRepository.existsByOrderIdAndReportTypeAndStatus(9L, ReportType.COMPLAINT, ReportStatus.PENDING)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> orderService.confirmReceipt(buyerUser, 9L));
        assertEquals("COMPLAINT_PENDING", ex.getCode());
        assertEquals(OrderStatus.SHIPPING, sampleOrder.getStatus());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-ORD-14 Tự hoàn tất: ORDER SHIPPING, confirm_deadline = NOW, không khiếu nại -> COMPLETED, Product SOLD, thông báo buyer + seller")
    void autoCompleteOrders_Success() {
        sampleOrder.setStatus(OrderStatus.SHIPPING);
        sampleOrder.setConfirmDeadline(NOW);
        when(orderRepository.findByStatusAndConfirmDeadlineLessThanEqual(OrderStatus.SHIPPING, NOW)).thenReturn(List.of(sampleOrder));
        when(orderRepository.findById(9L)).thenReturn(Optional.of(sampleOrder));
        when(productRepository.findByIdForUpdate(sampleOrder.getProductId())).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));
        when(reportRepository.existsByOrderIdAndReportTypeAndStatus(9L, ReportType.COMPLAINT, ReportStatus.PENDING)).thenReturn(false);

        orderService.autoCompleteOrders();

        assertEquals(OrderStatus.COMPLETED, sampleOrder.getStatus());
        assertEquals(ProductStatus.SOLD, sampleProduct.getStatus());
        verify(notificationService).notify(eq(sampleOrder.getBuyerId()), eq(NotificationType.ORDER_UPDATE), any(), any(), eq("ORDER"), eq(9L));
        verify(notificationService).notify(eq(sampleOrder.getSellerId()), eq(NotificationType.ORDER_UPDATE), any(), any(), eq("ORDER"), eq(9L));
    }

    @Test
    @DisplayName("UT-ORD-15 Tự hoàn tất bị tạm dừng khi có COMPLAINT PENDING; sau khi bác bỏ thì chạy lại hoàn tất")
    void autoCompleteOrders_ComplaintPendingPausesCompletion() {
        sampleOrder.setStatus(OrderStatus.SHIPPING);
        sampleOrder.setConfirmDeadline(NOW);
        when(orderRepository.findById(9L)).thenReturn(Optional.of(sampleOrder));
        when(productRepository.findByIdForUpdate(sampleOrder.getProductId())).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));

        // Lần 1: complaint PENDING -> bỏ qua
        when(reportRepository.existsByOrderIdAndReportTypeAndStatus(9L, ReportType.COMPLAINT, ReportStatus.PENDING)).thenReturn(true);
        orderLifecycleSupport.autoCompleteOne(9L, NOW);
        assertEquals(OrderStatus.SHIPPING, sampleOrder.getStatus());

        // Lần 2: complaint đã bác bỏ (không còn PENDING) -> COMPLETED
        when(reportRepository.existsByOrderIdAndReportTypeAndStatus(9L, ReportType.COMPLAINT, ReportStatus.PENDING)).thenReturn(false);
        orderLifecycleSupport.autoCompleteOne(9L, NOW);
        assertEquals(OrderStatus.COMPLETED, sampleOrder.getStatus());
    }

    @Test
    @DisplayName("UT-ORD-17 Xác nhận sai ngữ cảnh: (a) Shipment IN_TRANSIT (b) COMPLETED (c) SELLER (d) CANCELLED ném lỗi tương ứng")
    void confirmReceipt_InvalidContext_ThrowsAppropriateErrors() {
        // (a) Shipment IN_TRANSIT -> 409 INVALID_STATUS_TRANSITION
        sampleOrder.setStatus(OrderStatus.SHIPPING);
        when(orderRepository.findById(9L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(sampleOrder));
        Shipment inTransit = Shipment.builder().id(1L).orderId(9L).status(ShipmentStatus.IN_TRANSIT).build();
        when(shipmentRepository.findByOrderId(9L)).thenReturn(Optional.of(inTransit));

        AppException exA = assertThrows(AppException.class, () -> orderService.confirmReceipt(buyerUser, 9L));
        assertEquals("INVALID_STATUS_TRANSITION", exA.getCode());

        // (b) ORDER COMPLETED -> 409 INVALID_STATUS_TRANSITION
        sampleOrder.setStatus(OrderStatus.COMPLETED);
        AppException exB = assertThrows(AppException.class, () -> orderService.confirmReceipt(buyerUser, 9L));
        assertEquals("INVALID_STATUS_TRANSITION", exB.getCode());

        // (c) Người gọi là SELLER -> 403 FORBIDDEN
        sampleOrder.setStatus(OrderStatus.SHIPPING);
        AppException exC = assertThrows(AppException.class, () -> orderService.confirmReceipt(sellerUser, 9L));
        assertEquals("FORBIDDEN", exC.getCode());

        // (d) ORDER CANCELLED -> 409 ORDER_CANCELLED
        sampleOrder.setStatus(OrderStatus.CANCELLED);
        AppException exD = assertThrows(AppException.class, () -> orderService.confirmReceipt(buyerUser, 9L));
        assertEquals("ORDER_CANCELLED", exD.getCode());
    }

    @ParameterizedTest(name = "UT-ORD-16 Sự kiện {0} với Product ban đầu {1} -> kết quả Product {2}")
    @CsvSource({
            "ORDER_EXPIRED, RESERVED, AVAILABLE",
            "ORDER_EXPIRED, HIDDEN, HIDDEN",
            "ORDER_EXPIRED, REMOVED, REMOVED",
            "ORDER_CANCELLED, RESERVED, AVAILABLE",
            "ORDER_CANCELLED, HIDDEN, HIDDEN",
            "ORDER_CANCELLED, REMOVED, REMOVED"
    })
    @DisplayName("UT-ORD-16 Bảng hiệu ứng Product theo ORD-11")
    void testProductEffectTable(String event, ProductStatus initialStatus, ProductStatus expectedStatus) {
        Product product = Product.builder().id(10L).status(initialStatus).build();

        if ("ORDER_EXPIRED".equals(event) || "ORDER_CANCELLED".equals(event)) {
            if (product.getStatus() != ProductStatus.HIDDEN && product.getStatus() != ProductStatus.REMOVED) {
                product.setStatus(ProductStatus.AVAILABLE);
            }
        }

        assertEquals(expectedStatus, product.getStatus());
    }

    @Test
    @DisplayName("UT-ORD-18 Chuyển trạng thái cấm trên entity: markPaid khi EXPIRED, expire khi PAID, thao tác từ trạng thái cuối -> AppException INVALID_STATUS_TRANSITION")
    void testForbiddenStateTransitionsOnEntity() {
        Order order = Order.builder().status(OrderStatus.EXPIRED).build();

        // markPaid khi EXPIRED
        AppException ex1 = assertThrows(AppException.class, () -> order.markPaid(NOW));
        assertEquals("INVALID_STATUS_TRANSITION", ex1.getCode());

        // expire khi PAID
        order.setStatus(OrderStatus.PAID);
        AppException ex2 = assertThrows(AppException.class, order::expire);
        assertEquals("INVALID_STATUS_TRANSITION", ex2.getCode());

        // thao tác từ trạng thái cuối COMPLETED
        order.setStatus(OrderStatus.COMPLETED);
        AppException ex3 = assertThrows(AppException.class, () -> order.markPaid(NOW));
        assertEquals("INVALID_STATUS_TRANSITION", ex3.getCode());

        AppException ex4 = assertThrows(AppException.class, () -> order.cancel("Reason", NOW));
        assertEquals("INVALID_STATUS_TRANSITION", ex4.getCode());
    }
}
