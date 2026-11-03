package com.omnibid.service;

import com.omnibid.common.AppException;
import com.omnibid.entity.Order;
import com.omnibid.entity.Payment;
import com.omnibid.entity.Product;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.PaymentStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.*;
import com.omnibid.service.impl.AdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));

    private static final Long ADMIN_ID = 9L;
    private static final Long BUYER_ID = 2L;
    private static final Long SELLER_ID = 1L;
    private static final Long ORDER_ID = 9L;
    private static final Long PRODUCT_ID = 10L;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;

    private AdminServiceImpl adminService;

    private Product product;
    private Order order;

    @BeforeEach
    void setUp() {
        adminService = new AdminServiceImpl(
                productRepository,
                orderRepository,
                paymentRepository,
                auctionRepository,
                userRepository,
                auditService,
                notificationService,
                clock
        );

        product = Product.builder()
                .id(PRODUCT_ID)
                .sellerId(SELLER_ID)
                .title("San pham test")
                .status(ProductStatus.RESERVED)
                .build();

        order = Order.builder()
                .id(ORDER_ID)
                .buyerId(BUYER_ID)
                .sellerId(SELLER_ID)
                .productId(PRODUCT_ID)
                .status(OrderStatus.SHIPPING)
                .build();
    }

    @Test
    @DisplayName("completeOrder: Hoàn tất đơn hàng và đổi sản phẩm thành SOLD")
    void completeOrder_Success() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        Map<String, Object> effects = adminService.completeOrder(ORDER_ID, "Admin hoàn tất đơn", ADMIN_ID);

        assertThat(effects).containsEntry("orderStatus", "COMPLETED");
        assertThat(effects).containsEntry("productStatus", "SOLD");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getCompletedAt()).isEqualTo(NOW);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD);

        verify(auditService).log(ADMIN_ID, "ORDER_COMPLETE", "ORDER", ORDER_ID, "Admin hoàn tất đơn");
        verify(notificationService, times(2)).notify(anyLong(), any(), anyString(), anyString(), eq("ORDER"), eq(ORDER_ID));
    }

    @Test
    @DisplayName("completeOrder: Đơn đã COMPLETED -> 409 INVALID_STATUS_TRANSITION")
    void completeOrder_AlreadyCompleted_ThrowsConflict() {
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> adminService.completeOrder(ORDER_ID, "Ghi chu", ADMIN_ID))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("INVALID_STATUS_TRANSITION") && ((AppException) e).getStatus() == HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("cancelOrder: Hủy đơn hàng, chuyển pending payment thành FAILED, đổi Product thành AVAILABLE")
    void cancelOrder_Success() {
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        Payment pendingPayment = Payment.builder()
                .id(100L)
                .orderId(ORDER_ID)
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(pendingPayment));

        Map<String, Object> effects = adminService.cancelOrder(ORDER_ID, "Admin hủy đơn khiếu nại", ADMIN_ID);

        assertThat(effects).containsEntry("orderStatus", "CANCELLED");
        assertThat(effects).containsEntry("productStatus", "AVAILABLE");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isEqualTo(NOW);
        assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(pendingPayment.getFailureReason()).isEqualTo("ORDER_CANCELLED");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.AVAILABLE);

        verify(auditService).log(ADMIN_ID, "ORDER_CANCEL", "ORDER", ORDER_ID, "Admin hủy đơn khiếu nại");
        verify(notificationService, times(2)).notify(anyLong(), any(), anyString(), anyString(), eq("ORDER"), eq(ORDER_ID));
    }

    @Test
    @DisplayName("cancelOrder: Đơn đã CANCELLED -> 409 INVALID_STATUS_TRANSITION")
    void cancelOrder_AlreadyCancelled_ThrowsConflict() {
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> adminService.cancelOrder(ORDER_ID, "Hủy đơn", ADMIN_ID))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("INVALID_STATUS_TRANSITION") && ((AppException) e).getStatus() == HttpStatus.CONFLICT);
    }
}
