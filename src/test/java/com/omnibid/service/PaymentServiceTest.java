package com.omnibid.service;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.payment.PaymentHistoryResponse;
import com.omnibid.dto.payment.PaymentRequest;
import com.omnibid.dto.payment.PaymentResultResponse;
import com.omnibid.entity.Order;
import com.omnibid.entity.Payment;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.PaymentMethod;
import com.omnibid.enums.PaymentStatus;
import com.omnibid.gateway.MockPaymentGateway;
import com.omnibid.gateway.MockPaymentGateway.GatewayRequest;
import com.omnibid.gateway.MockPaymentGateway.GatewayResult;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.PaymentRepository;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.OrderAccessPolicy;
import com.omnibid.service.impl.PaymentServiceImpl;
import com.omnibid.test.DirectTransactionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Long BUYER_ID = 2L;
    private static final Long SELLER_ID = 1L;
    private static final Long OTHER_ID = 3L;
    private static final Long ADMIN_ID = 9L;
    private static final Long ORDER_ID = 9L;

    private static final String KEY_A = "9b2f6c1e-0a7d-4c55-9f0e-6f3a1d2b7c01";
    private static final String KEY_B = "9b2f6c1e-0a7d-4c55-9f0e-6f3a1d2b7c02";

    private final CurrentUser buyerUser = new CurrentUser(BUYER_ID, false);
    private final CurrentUser sellerUser = new CurrentUser(SELLER_ID, false);
    private final CurrentUser otherUser = new CurrentUser(OTHER_ID, false);
    private final CurrentUser adminUser = new CurrentUser(ADMIN_ID, true);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MockPaymentGateway gateway;

    @Mock
    private NotificationService notificationService;

    private PaymentServiceImpl service;
    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(
                orderRepository,
                paymentRepository,
                new OrderAccessPolicy(),
                gateway,
                notificationService,
                clock,
                new DirectTransactionTemplate()
        );

        sampleOrder = Order.builder()
                .id(ORDER_ID)
                .auctionId(100L)
                .productId(10L)
                .sellerId(SELLER_ID)
                .buyerId(BUYER_ID)
                .finalPrice(BigDecimal.valueOf(520000))
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentDeadline(NOW.plus(Duration.ofHours(48)))
                .shippingAddress("123 Le Loi, Q1, HCMC")
                .phoneNumber("0901234567")
                .build();
    }

    @Test
    @DisplayName("Thanh toán SUCCESS: tạo Payment SUCCESS, Order PAID, thông báo buyer + seller")
    void pay_Success_MarksOrderPaidAndNotifiesBoth() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.empty());
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.SUCCESS)).thenReturn(false);
        when(paymentRepository.findMaxAttemptNo(ORDER_ID)).thenReturn(0);
        when(gateway.charge(any(GatewayRequest.class))).thenReturn(new GatewayResult(PaymentStatus.SUCCESS, null));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(15L);
            return p;
        });

        PaymentResultResponse res = service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null));

        assertThat(res.getPaymentId()).isEqualTo(15L);
        assertThat(res.getAttemptNo()).isEqualTo(1);
        assertThat(res.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(res.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(res.getFailureReason()).isNull();
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(sampleOrder.getPaidAt()).isEqualTo(NOW);

        verify(orderRepository).save(sampleOrder);
        verify(notificationService).notify(eq(BUYER_ID), eq(NotificationType.ORDER_UPDATE), any(), any(), eq("ORDER"), eq(ORDER_ID));
        verify(notificationService).notify(eq(SELLER_ID), eq(NotificationType.ORDER_UPDATE), any(), any(), eq("ORDER"), eq(ORDER_ID));
    }

    @Test
    @DisplayName("Thanh toán FAILED: trả MOCK_DECLINED, Order giữ PENDING_PAYMENT, không thông báo")
    void pay_Failed_KeepsOrderPending() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.empty());
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.SUCCESS)).thenReturn(false);
        when(paymentRepository.findMaxAttemptNo(ORDER_ID)).thenReturn(0);
        when(gateway.charge(any(GatewayRequest.class))).thenReturn(new GatewayResult(PaymentStatus.FAILED, "MOCK_DECLINED"));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(16L);
            return p;
        });

        PaymentResultResponse res = service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, PaymentStatus.FAILED));

        assertThat(res.getPaymentId()).isEqualTo(16L);
        assertThat(res.getAttemptNo()).isEqualTo(1);
        assertThat(res.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(res.getOrderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(res.getFailureReason()).isEqualTo("MOCK_DECLINED");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Thử lại sau thất bại tăng attemptNo lên 2 và lấy đúng số tiền từ finalPrice")
    void pay_Retry_IncrementsAttemptNoAndUsesOrderFinalPrice() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_B)).thenReturn(Optional.empty());
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.SUCCESS)).thenReturn(false);
        when(paymentRepository.findMaxAttemptNo(ORDER_ID)).thenReturn(1);

        ArgumentCaptor<GatewayRequest> gwCaptor = ArgumentCaptor.forClass(GatewayRequest.class);
        when(gateway.charge(gwCaptor.capture())).thenReturn(new GatewayResult(PaymentStatus.SUCCESS, null));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(17L);
            return p;
        });

        PaymentResultResponse res = service.pay(buyerUser, ORDER_ID, KEY_B, new PaymentRequest(PaymentMethod.MOCK_CARD, null));

        assertThat(res.getAttemptNo()).isEqualTo(2);
        assertThat(gwCaptor.getValue().amount()).isEqualByComparingTo(BigDecimal.valueOf(520000));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-a-uuid", "12345678", "9b2f6c1e-0a7d-4c55-9f0e-6f3a1d2b7c0g"})
    @DisplayName("Idempotency-Key không phải UUID hợp lệ ném 400 VALIDATION_ERROR")
    void pay_InvalidUuidKey_ThrowsValidationError(String invalidKey) {
        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, invalidKey, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(orderRepository, gateway);
    }

    @Test
    @DisplayName("Gửi lặp cùng Idempotency-Key trả lại kết quả cũ, không gọi gateway")
    void pay_DuplicateKey_ReturnsExistingResult() {
        sampleOrder.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        Payment existing = Payment.builder()
                .id(21L).orderId(ORDER_ID).attemptNo(1).status(PaymentStatus.SUCCESS).build();
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.of(existing));

        PaymentResultResponse res = service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null));

        assertThat(res.getPaymentId()).isEqualTo(21L);
        assertThat(res.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(res.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("Bắt DataIntegrityViolationException: có bản ghi theo key -> trả kết quả cũ; không có -> CONFLICT")
    void pay_UniqueViolation_RechecksKeyOrThrowsConflict() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.SUCCESS)).thenReturn(false);
        when(paymentRepository.findMaxAttemptNo(ORDER_ID)).thenReturn(0);
        when(gateway.charge(any())).thenReturn(new GatewayResult(PaymentStatus.SUCCESS, null));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        Payment existing = Payment.builder().id(30L).orderId(ORDER_ID).attemptNo(1).status(PaymentStatus.SUCCESS).build();
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));

        PaymentResultResponse res = service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null));
        assertThat(res.getPaymentId()).isEqualTo(30L);

        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_B)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_B, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    @DisplayName("Người không phải buyer (seller, other, admin) nhận 403 FORBIDDEN")
    void pay_NonBuyer_ThrowsForbidden() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));

        for (CurrentUser user : List.of(sellerUser, otherUser, adminUser)) {
            assertThatThrownBy(() -> service.pay(user, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.FORBIDDEN));
        }
    }

    @Test
    @DisplayName("Trạng thái CANCELLED / EXPIRED / quá hạn / PAID / thiếu địa chỉ ném mã lỗi v4")
    void pay_StatusDeadlineAndShippingChecks() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.empty());

        sampleOrder.setStatus(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.ORDER_CANCELLED));

        sampleOrder.setStatus(OrderStatus.EXPIRED);
        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.ORDER_EXPIRED));

        sampleOrder.setStatus(OrderStatus.PAID);
        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));

        // now == paymentDeadline -> ORDER_EXPIRED
        sampleOrder.setStatus(OrderStatus.PENDING_PAYMENT);
        sampleOrder.setPaymentDeadline(NOW);
        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.ORDER_EXPIRED));

        // Missing shipping address -> VALIDATION_ERROR
        sampleOrder.setPaymentDeadline(NOW.plus(Duration.ofHours(1)));
        sampleOrder.setShippingAddress("   ");
        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("GET /orders/{id}/payments: chỉ buyer được xem, phân trang và kiểm tra sort whitelist")
    void getPaymentsByOrder_BuyerOnlyAndPaginated() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
        Payment p = Payment.builder()
                .id(15L).orderId(ORDER_ID).attemptNo(1).amount(BigDecimal.valueOf(520000))
                .method(PaymentMethod.MOCK_CARD).status(PaymentStatus.SUCCESS).createdAt(NOW).paidAt(NOW).build();
        when(paymentRepository.findByOrderId(eq(ORDER_ID), any())).thenReturn(new PageImpl<>(List.of(p)));

        PageResponse<PaymentHistoryResponse> page = service.getPaymentsByOrder(
                buyerUser, ORDER_ID, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "attemptNo"))
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(15L);

        assertThatThrownBy(() -> service.getPaymentsByOrder(sellerUser, ORDER_ID, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.getPaymentsByOrder(adminUser, ORDER_ID, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.getPaymentsByOrder(buyerUser, ORDER_ID, PageRequest.of(0, 10, Sort.by("badField"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
