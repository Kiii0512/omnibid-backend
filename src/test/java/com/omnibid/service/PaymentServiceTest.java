package com.omnibid.service;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.payment.PaymentHistoryResponse;
import com.omnibid.dto.payment.PaymentRequest;
import com.omnibid.dto.payment.PaymentResponse;
import com.omnibid.entity.Order;
import com.omnibid.entity.Payment;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.PaymentMethod;
import com.omnibid.enums.PaymentStatus;
import com.omnibid.gateway.MockPaymentGateway;
import com.omnibid.gateway.MockPaymentGateway.GatewayRequest;
import com.omnibid.gateway.MockPaymentGateway.GatewayResult;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.PaymentRepository;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionOperations;

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
class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Long BUYER_ID = 2L;
    private static final Long SELLER_ID = 1L;
    private static final Long ORDER_ID = 9L;
    private static final String VALID_KEY = "9b2f6c1e-0a7d-4c55-9f0e-6f3a1d2b7c01";

    private final CurrentUser buyerUser = new CurrentUser(BUYER_ID, false);
    private final CurrentUser sellerUser = new CurrentUser(SELLER_ID, false);

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private MockPaymentGateway gateway;
    @Mock
    private NotificationService notificationService;

    private PaymentServiceImpl paymentService;
    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        TransactionOperations directTx = TransactionOperations.withoutTransaction();
        AppProperties appProperties = new AppProperties();

        paymentService = new PaymentServiceImpl(
                orderRepository,
                paymentRepository,
                gateway,
                notificationService,
                directTx,
                appProperties.getPayment(),
                clock
        );

        sampleOrder = Order.builder()
                .id(ORDER_ID)
                .auctionId(12L)
                .productId(4L)
                .sellerId(SELLER_ID)
                .buyerId(BUYER_ID)
                .finalPrice(BigDecimal.valueOf(520000))
                .shippingAddress("123 Street")
                .phoneNumber("0900000000")
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentDeadline(NOW.plus(48, ChronoUnit.HOURS))
                .createdAt(NOW)
                .build();
    }

    @Test
    @DisplayName("pay — thành công chuyển Order sang PAID và trả PaymentResponse SUCCESS")
    void pay_Success() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, VALID_KEY)).thenReturn(Optional.empty());
        when(paymentRepository.findMaxAttemptNo(ORDER_ID)).thenReturn(Optional.of(0));

        GatewayResult gatewayResult = new GatewayResult(PaymentStatus.SUCCESS, null);
        when(gateway.charge(any(GatewayRequest.class))).thenReturn(gatewayResult);

        Payment savedPayment = Payment.builder()
                .id(16L)
                .orderId(ORDER_ID)
                .attemptNo(1)
                .amount(BigDecimal.valueOf(520000))
                .status(PaymentStatus.SUCCESS)
                .createdAt(NOW)
                .build();
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenReturn(savedPayment);

        PaymentRequest request = new PaymentRequest(PaymentMethod.MOCK_CARD, null);
        PaymentResponse response = paymentService.pay(buyerUser, ORDER_ID, VALID_KEY, request);

        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertEquals(OrderStatus.PAID, response.getOrderStatus());
        assertEquals(OrderStatus.PAID, sampleOrder.getStatus());
        assertEquals(NOW, sampleOrder.getPaidAt());
        verify(orderRepository).save(sampleOrder);
    }

    @Test
    @DisplayName("pay — thất bại lưu Payment FAILED, Order giữ PENDING_PAYMENT")
    void pay_Failed() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, VALID_KEY)).thenReturn(Optional.empty());
        when(paymentRepository.findMaxAttemptNo(ORDER_ID)).thenReturn(Optional.of(0));

        GatewayResult gatewayResult = new GatewayResult(PaymentStatus.FAILED, "MOCK_DECLINED");
        when(gateway.charge(any(GatewayRequest.class))).thenReturn(gatewayResult);

        Payment savedPayment = Payment.builder()
                .id(17L)
                .orderId(ORDER_ID)
                .attemptNo(1)
                .amount(BigDecimal.valueOf(520000))
                .status(PaymentStatus.FAILED)
                .failureReason("MOCK_DECLINED")
                .createdAt(NOW)
                .build();
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenReturn(savedPayment);

        PaymentRequest request = new PaymentRequest(PaymentMethod.MOCK_CARD, PaymentStatus.FAILED);
        PaymentResponse response = paymentService.pay(buyerUser, ORDER_ID, VALID_KEY, request);

        assertNotNull(response);
        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertEquals("MOCK_DECLINED", response.getFailureReason());
        assertEquals(OrderStatus.PENDING_PAYMENT, sampleOrder.getStatus());
    }

    @Test
    @DisplayName("pay — cùng idempotency key trả lại payment đã tồn tại")
    void pay_IdempotencyKey_ReturnsExisting() {
        Payment existing = Payment.builder()
                .id(16L)
                .orderId(ORDER_ID)
                .attemptNo(1)
                .amount(BigDecimal.valueOf(520000))
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey(VALID_KEY)
                .createdAt(NOW)
                .build();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, VALID_KEY)).thenReturn(Optional.of(existing));

        PaymentRequest request = new PaymentRequest(PaymentMethod.MOCK_CARD, null);
        PaymentResponse response = paymentService.pay(buyerUser, ORDER_ID, VALID_KEY, request);

        assertNotNull(response);
        assertEquals(16L, response.getPaymentId());
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        verify(gateway, never()).charge(any());
    }

    @Test
    @DisplayName("pay — idempotency key sai định dạng UUID bị VALIDATION_ERROR")
    void pay_InvalidIdempotencyKey_ThrowsValidationError() {
        PaymentRequest request = new PaymentRequest(PaymentMethod.MOCK_CARD, null);
        assertThatThrownBy(() -> paymentService.pay(buyerUser, ORDER_ID, "not-a-uuid", request))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("pay — không phải buyer bị 403 FORBIDDEN")
    void pay_NotBuyer_ThrowsForbidden() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));

        PaymentRequest request = new PaymentRequest(PaymentMethod.MOCK_CARD, null);
        assertThatThrownBy(() -> paymentService.pay(sellerUser, ORDER_ID, VALID_KEY, request))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("pay — đơn quá hạn thanh toán chuyển EXPIRED và ném ORDER_EXPIRED")
    void pay_OverdueOrder_ThrowsOrderExpired() {
        sampleOrder.setPaymentDeadline(NOW.minus(1, ChronoUnit.MINUTES));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));

        PaymentRequest request = new PaymentRequest(PaymentMethod.MOCK_CARD, null);
        assertThatThrownBy(() -> paymentService.pay(buyerUser, ORDER_ID, VALID_KEY, request))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.ORDER_EXPIRED);
    }

    @Test
    @DisplayName("getPaymentsByOrder — buyer xem lịch sử thanh toán 200")
    void getPaymentsByOrder_Success() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(sampleOrder));

        Payment p1 = Payment.builder()
                .id(16L)
                .orderId(ORDER_ID)
                .attemptNo(1)
                .amount(BigDecimal.valueOf(520000))
                .method(PaymentMethod.MOCK_CARD)
                .status(PaymentStatus.SUCCESS)
                .createdAt(NOW)
                .build();
        when(paymentRepository.findByOrderId(eq(ORDER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(p1), PageRequest.of(0, 20), 1));

        Page<PaymentHistoryResponse> result = paymentService.getPaymentsByOrder(buyerUser, ORDER_ID, PageRequest.of(0, 20));
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(16L, result.getContent().get(0).getPaymentId());
    }
}
