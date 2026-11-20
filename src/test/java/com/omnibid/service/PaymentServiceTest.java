package com.omnibid.service;

import com.omnibid.common.AppException;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.payment.PaymentHistoryResponse;
import com.omnibid.dto.payment.PaymentRequest;
import com.omnibid.dto.payment.PaymentResponse;
import com.omnibid.entity.Order;
import com.omnibid.entity.Payment;
import com.omnibid.entity.Shipment;
import com.omnibid.entity.User;
import com.omnibid.enums.*;
import com.omnibid.gateway.MockPaymentGateway;
import com.omnibid.gateway.MockPaymentGateway.GatewayRequest;
import com.omnibid.gateway.MockPaymentGateway.GatewayResult;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.PaymentRepository;
import com.omnibid.repository.ShipmentRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.impl.PaymentServiceImpl;
import com.omnibid.test.DirectTransactionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));

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
    private ShipmentService shipmentService;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

    private AppProperties.PaymentProperties paymentProperties;
    private PaymentServiceImpl service;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        paymentProperties = AppProperties.PaymentProperties.defaults();
        service = new PaymentServiceImpl(
                orderRepository,
                paymentRepository,
                gateway,
                shipmentService,
                shipmentRepository,
                notificationService,
                new DirectTransactionTemplate(),
                paymentProperties,
                clock,
                userRepository
        );

        sampleOrder = Order.builder()
                .id(ORDER_ID)
                .auctionId(100L)
                .productId(10L)
                .sellerId(SELLER_ID)
                .buyerId(BUYER_ID)
                .finalPrice(BigDecimal.valueOf(520000))
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentDeadline(NOW.plusHours(48))
                .shippingAddress("123 Le Loi, Q1, HCMC")
                .phoneNumber("0901234567")
                .build();
    }

    private void mockOrderLock(Order order) {
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
    }

    @Test
    @DisplayName("UT-PAY-01 Thanh toán thành công tạo Payment SUCCESS, Order PAID, Shipment CREATED")
    void pay_Success_CreatesPaymentSuccessOrderPaidAndShipment() {
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.empty());
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.findTop5ByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(Collections.emptyList());
        when(paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(Optional.empty());

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(15L);
            return p;
        });

        when(gateway.charge(any(GatewayRequest.class))).thenReturn(new GatewayResult(SimulatedOutcome.SUCCESS, null));
        when(paymentRepository.findByTransactionRef(anyString())).thenAnswer(inv -> {
            Payment p = Payment.builder()
                    .id(15L)
                    .orderId(ORDER_ID)
                    .attemptNo(1)
                    .amount(sampleOrder.getFinalPrice())
                    .status(PaymentStatus.PENDING)
                    .transactionRef(inv.getArgument(0))
                    .build();
            return Optional.of(p);
        });
        when(paymentRepository.findByIdForUpdate(15L)).thenAnswer(inv -> {
            Payment p = Payment.builder()
                    .id(15L)
                    .orderId(ORDER_ID)
                    .attemptNo(1)
                    .amount(sampleOrder.getFinalPrice())
                    .status(PaymentStatus.PENDING)
                    .transactionRef("MOCK-TX")
                    .build();
            return Optional.of(p);
        });

        Shipment shipment = Shipment.builder()
                .id(4L)
                .orderId(ORDER_ID)
                .trackingCode("SIM-000009")
                .status(ShipmentStatus.CREATED)
                .build();
        when(shipmentService.createForPaidOrder(sampleOrder)).thenReturn(shipment);

        PaymentResponse response = service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null));

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getAttemptNo()).isEqualTo(1);
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(response.getShipment()).isNotNull();
        assertThat(response.getShipment().getTrackingCode()).isEqualTo("SIM-000009");
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(sampleOrder.getPaidAt()).isEqualTo(NOW);

        verify(shipmentService, times(1)).createForPaidOrder(sampleOrder);
        verify(notificationService).notify(eq(BUYER_ID), eq(NotificationType.PAYMENT_RESULT), any(), any(), eq("ORDER"), eq(ORDER_ID));
        verify(notificationService).notify(eq(SELLER_ID), eq(NotificationType.PAYMENT_RESULT), any(), any(), eq("ORDER"), eq(ORDER_ID));
    }

    @Test
    @DisplayName("UT-PAY-02 Bộ mô phỏng từ chối: Payment FAILED, Order giữ PENDING_PAYMENT, không tạo Shipment")
    void pay_SimulatorDeclined_PaymentFailedOrderKeepsPending() {
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.empty());
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.findTop5ByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(Collections.emptyList());
        when(paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(Optional.empty());

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(16L);
            return p;
        });

        when(gateway.charge(any(GatewayRequest.class))).thenReturn(new GatewayResult(SimulatedOutcome.FAILED, "MOCK_DECLINED"));
        when(paymentRepository.findByTransactionRef(anyString())).thenAnswer(inv -> {
            Payment p = Payment.builder()
                    .id(16L)
                    .orderId(ORDER_ID)
                    .attemptNo(1)
                    .amount(sampleOrder.getFinalPrice())
                    .status(PaymentStatus.PENDING)
                    .transactionRef(inv.getArgument(0))
                    .build();
            return Optional.of(p);
        });
        when(paymentRepository.findByIdForUpdate(16L)).thenAnswer(inv -> {
            Payment p = Payment.builder()
                    .id(16L)
                    .orderId(ORDER_ID)
                    .attemptNo(1)
                    .amount(sampleOrder.getFinalPrice())
                    .status(PaymentStatus.PENDING)
                    .transactionRef("MOCK-TX")
                    .build();
            return Optional.of(p);
        });

        PaymentResponse response = service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null));

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getFailureReason()).isEqualTo("MOCK_DECLINED");
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        verify(shipmentService, never()).createForPaidOrder(any());
        verify(notificationService).notify(eq(BUYER_ID), eq(NotificationType.PAYMENT_RESULT), any(), any(), eq("ORDER"), eq(ORDER_ID));
        verify(notificationService, never()).notify(eq(SELLER_ID), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("UT-PAY-03 Thử lại sau thất bại: attempt_no tăng lên 2, thành công")
    void pay_RetryAfterFailure_AttemptNoIncrements() {
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_B)).thenReturn(Optional.empty());
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.findTop5ByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(Collections.emptyList());

        Payment failedFirst = Payment.builder().id(10L).attemptNo(1).status(PaymentStatus.FAILED).build();
        when(paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(Optional.of(failedFirst));

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(17L);
            return p;
        });

        when(gateway.charge(any(GatewayRequest.class))).thenReturn(new GatewayResult(SimulatedOutcome.SUCCESS, null));
        when(paymentRepository.findByTransactionRef(anyString())).thenAnswer(inv -> Optional.of(Payment.builder().id(17L).orderId(ORDER_ID).attemptNo(2).status(PaymentStatus.PENDING).transactionRef("MOCK-2").build()));
        when(paymentRepository.findByIdForUpdate(17L)).thenAnswer(inv -> Optional.of(Payment.builder().id(17L).orderId(ORDER_ID).attemptNo(2).status(PaymentStatus.PENDING).transactionRef("MOCK-2").build()));

        Shipment shipment = Shipment.builder().id(5L).orderId(ORDER_ID).trackingCode("SIM-000009").status(ShipmentStatus.CREATED).build();
        when(shipmentService.createForPaidOrder(sampleOrder)).thenReturn(shipment);

        PaymentResponse response = service.pay(buyerUser, ORDER_ID, KEY_B, new PaymentRequest(PaymentMethod.MOCK_BANK, null));

        assertThat(response.getAttemptNo()).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234567", "12345678901234567890123456789012345678901234567890123456789012345", "invalid/key"})
    @DisplayName("UT-PAY-04 Header Idempotency-Key không hợp lệ ném 400 VALIDATION_ERROR")
    void pay_InvalidIdempotencyKey_ThrowsValidationError(String invalidKey) {
        mockOrderLock(sampleOrder);

        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, invalidKey, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("UT-PAY-04 Biên hợp lệ 8 ký tự và 64 ký tự được chấp nhận")
    void pay_BoundaryIdempotencyKey_Accepted() {
        mockOrderLock(sampleOrder);
        String key8 = "12345678";
        String key64 = "1234567890123456789012345678901234567890123456789012345678901234";

        when(paymentRepository.findByOrderIdAndIdempotencyKey(eq(ORDER_ID), anyString())).thenReturn(Optional.empty());
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.findTop5ByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(Collections.emptyList());
        when(paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });
        when(gateway.charge(any())).thenReturn(new GatewayResult(SimulatedOutcome.SUCCESS, null));
        when(paymentRepository.findByTransactionRef(anyString())).thenAnswer(inv -> Optional.of(Payment.builder().id(100L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(paymentRepository.findByIdForUpdate(100L)).thenAnswer(inv -> Optional.of(Payment.builder().id(100L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(shipmentService.createForPaidOrder(any())).thenReturn(Shipment.builder().id(1L).trackingCode("SIM-000009").status(ShipmentStatus.CREATED).build());

        PaymentResponse res8 = service.pay(buyerUser, ORDER_ID, key8, new PaymentRequest(PaymentMethod.MOCK_CARD, null));
        assertThat(res8.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        sampleOrder.setStatus(OrderStatus.PENDING_PAYMENT);
        PaymentResponse res64 = service.pay(buyerUser, ORDER_ID, key64, new PaymentRequest(PaymentMethod.MOCK_CARD, null));
        assertThat(res64.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("UT-PAY-05 Gửi lặp cùng key đã có kết quả: trả lại kết quả cũ, gateway và DB save không được gọi")
    void pay_DuplicateKeyWithResult_ReturnsOldResultImmediately() {
        mockOrderLock(sampleOrder);
        Payment existingPayment = Payment.builder()
                .id(20L)
                .orderId(ORDER_ID)
                .attemptNo(1)
                .status(PaymentStatus.FAILED)
                .failureReason("MOCK_DECLINED")
                .transactionRef("OLD-TX")
                .build();
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.of(existingPayment));

        PaymentResponse response = service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_WALLET, null));

        assertThat(response.getPaymentId()).isEqualTo(20L);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getFailureReason()).isEqualTo("MOCK_DECLINED");

        verifyNoInteractions(gateway);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-PAY-06 Gửi lặp key sau khi đơn đã PAID: trả lại kết quả SUCCESS (bước 4 trước bước 6-9)")
    void pay_DuplicateKeyWhenOrderAlreadyPaid_ReturnsSuccess() {
        sampleOrder.setStatus(OrderStatus.PAID);
        mockOrderLock(sampleOrder);

        Payment existingPayment = Payment.builder()
                .id(21L)
                .orderId(ORDER_ID)
                .attemptNo(1)
                .status(PaymentStatus.SUCCESS)
                .transactionRef("PAID-TX")
                .build();
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.of(existingPayment));

        Shipment shipment = Shipment.builder().id(3L).trackingCode("SIM-000009").status(ShipmentStatus.CREATED).build();
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(shipment));

        PaymentResponse response = service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null));

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getPaymentId()).isEqualTo(21L);
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(response.getShipment()).isNotNull();
        assertThat(response.getShipment().getTrackingCode()).isEqualTo("SIM-000009");
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("UT-PAY-07 Key mới khi đang có PENDING bị 409 PAYMENT_IN_PROGRESS; gửi key cũ trả PENDING")
    void pay_PendingExists_NewKeyBlockedOldKeyReturnsPending() {
        mockOrderLock(sampleOrder);

        // Case 1: Send with KEY_B (new key) while KEY_A is PENDING
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_B)).thenReturn(Optional.empty());
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_B, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getCode()).isEqualTo("PAYMENT_IN_PROGRESS");
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        // Case 2: Re-send with KEY_A (old key) -> returns old PENDING result
        Payment existingPending = Payment.builder()
                .id(30L)
                .orderId(ORDER_ID)
                .attemptNo(1)
                .status(PaymentStatus.PENDING)
                .transactionRef("TX-PENDING")
                .build();
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.of(existingPending));

        PaymentResponse resOldKey = service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null));
        assertThat(resOldKey.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(resOldKey.getPaymentId()).isEqualTo(30L);
    }

    @Test
    @DisplayName("UT-PAY-08 Đơn đã PAID với key mới ném 409 INVALID_STATUS_TRANSITION")
    void pay_OrderPaid_WithNewKey_ThrowsInvalidStatusTransition() {
        sampleOrder.setStatus(OrderStatus.PAID);
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_B)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_B, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getCode()).isEqualTo("INVALID_STATUS_TRANSITION");
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    @DisplayName("UT-PAY-09 Quá hạn thanh toán ném 409 ORDER_EXPIRED; chưa quá hạn thì được tạo Payment")
    void pay_PaymentDeadlineBoundary_ThrowsOrderExpiredOrProceeds() {
        // Case 1: NOW = deadline
        sampleOrder.setPaymentDeadline(NOW);
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("ORDER_EXPIRED"));

        // Case 2: NOW = deadline + 1s
        sampleOrder.setPaymentDeadline(NOW.minusSeconds(1));
        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("ORDER_EXPIRED"));

        // Case 3: NOW = deadline - 1s -> allowed
        sampleOrder.setPaymentDeadline(NOW.plusSeconds(1));
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.findTop5ByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(Collections.emptyList());
        when(paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(40L);
            return p;
        });
        when(gateway.charge(any())).thenReturn(new GatewayResult(SimulatedOutcome.SUCCESS, null));
        when(paymentRepository.findByTransactionRef(anyString())).thenAnswer(inv -> Optional.of(Payment.builder().id(40L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(paymentRepository.findByIdForUpdate(40L)).thenAnswer(inv -> Optional.of(Payment.builder().id(40L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(shipmentService.createForPaidOrder(any())).thenReturn(Shipment.builder().id(1L).trackingCode("SIM-000009").status(ShipmentStatus.CREATED).build());

        PaymentResponse allowedResponse = service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null));
        assertThat(allowedResponse.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("UT-PAY-10 Yêu cầu thứ 5 trong 10 phút được tạo Payment #5")
    void pay_FifthRequestWithinWindow_Allowed() {
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_B)).thenReturn(Optional.empty());
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(false);

        List<Payment> recent4 = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            recent4.add(Payment.builder().id((long) i).orderId(ORDER_ID).status(PaymentStatus.FAILED).createdAt(NOW.minusMinutes(2)).build());
        }
        when(paymentRepository.findTop5ByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(recent4);
        when(paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(Optional.of(recent4.get(0)));

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(5L);
            return p;
        });
        when(gateway.charge(any())).thenReturn(new GatewayResult(SimulatedOutcome.SUCCESS, null));
        when(paymentRepository.findByTransactionRef(anyString())).thenAnswer(inv -> Optional.of(Payment.builder().id(5L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(paymentRepository.findByIdForUpdate(5L)).thenAnswer(inv -> Optional.of(Payment.builder().id(5L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(shipmentService.createForPaidOrder(any())).thenReturn(Shipment.builder().id(1L).trackingCode("SIM-000009").status(ShipmentStatus.CREATED).build());

        PaymentResponse response = service.pay(buyerUser, ORDER_ID, KEY_B, new PaymentRequest(PaymentMethod.MOCK_CARD, null));
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("UT-PAY-11 Yêu cầu thứ 6 trong 10 phút bị 429 với retryAfterSeconds đúng = 312; cũ nhất ở 10m thì được phép")
    void pay_SixthRequestWithinWindow_ReturnsRateLimited() {
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_B)).thenReturn(Optional.empty());

        // 5 payments, oldest created at NOW - 4m48s -> window expiry in 312 seconds
        List<Payment> recent5 = new ArrayList<>();
        LocalDateTime oldestTime = NOW.minusMinutes(4).minusSeconds(48);
        for (int i = 1; i <= 5; i++) {
            LocalDateTime createdTime = i == 5 ? oldestTime : NOW.minusMinutes(2);
            recent5.add(Payment.builder().id((long) i).orderId(ORDER_ID).status(PaymentStatus.FAILED).createdAt(createdTime).build());
        }
        when(paymentRepository.findTop5ByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(recent5);

        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_B, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getCode()).isEqualTo("PAYMENT_RATE_LIMITED");
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(appEx.getData()).isNotNull();
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> map = (java.util.Map<String, Object>) appEx.getData();
                    assertThat(map.get("retryAfterSeconds")).isEqualTo(312L);
                });

        verifyNoInteractions(gateway);
        verify(paymentRepository, never()).save(any());

        // Oldest created exactly 10 minutes ago -> allowed
        recent5.get(4).setCreatedAt(NOW.minusMinutes(10));
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(Optional.of(recent5.get(0)));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(6L);
            return p;
        });
        when(gateway.charge(any())).thenReturn(new GatewayResult(SimulatedOutcome.SUCCESS, null));
        when(paymentRepository.findByTransactionRef(anyString())).thenAnswer(inv -> Optional.of(Payment.builder().id(6L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(paymentRepository.findByIdForUpdate(6L)).thenAnswer(inv -> Optional.of(Payment.builder().id(6L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(shipmentService.createForPaidOrder(any())).thenReturn(Shipment.builder().id(1L).trackingCode("SIM-000009").status(ShipmentStatus.CREATED).build());

        PaymentResponse allowedResp = service.pay(buyerUser, ORDER_ID, KEY_B, new PaymentRequest(PaymentMethod.MOCK_CARD, null));
        assertThat(allowedResp.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("UT-PAY-12 Người gọi không phải buyer (SELLER, OTHER, ADMIN) nhận 403 FORBIDDEN")
    void pay_NonBuyer_ThrowsForbidden() {
        mockOrderLock(sampleOrder);

        assertThatThrownBy(() -> service.pay(sellerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("FORBIDDEN"));

        assertThatThrownBy(() -> service.pay(otherUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("FORBIDDEN"));

        assertThatThrownBy(() -> service.pay(adminUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("FORBIDDEN"));
    }

    @Test
    @DisplayName("UT-PAY-13 Đơn CANCELLED hoặc EXPIRED ném 409 tương ứng")
    void pay_CancelledOrExpiredOrder_ThrowsConflict() {
        sampleOrder.setStatus(OrderStatus.CANCELLED);
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("ORDER_CANCELLED"));

        sampleOrder.setStatus(OrderStatus.EXPIRED);
        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("ORDER_EXPIRED"));
    }

    @Test
    @DisplayName("UT-PAY-14 Thiếu thông tin giao hàng ném 400 VALIDATION_ERROR; buyer BANNED ném 403 ACCOUNT_BANNED")
    void pay_MissingShippingOrBannedBuyer_ThrowsValidationErrorOrBanned() {
        // (a) shipping_address = null
        sampleOrder.setShippingAddress(null);
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("VALIDATION_ERROR"));

        // (b) Buyer BANNED
        sampleOrder.setShippingAddress("123 Le Loi");
        User bannedBuyer = User.builder().id(BUYER_ID).status(UserStatus.BANNED).build();
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(bannedBuyer));

        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getCode()).isEqualTo("ACCOUNT_BANNED");
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    @DisplayName("UT-PAY-15 Hai pha: InOrder kiểm tra TX1 lock Order -> save Payment PENDING trước gateway.charge trước TX2 lock Order -> lock Payment -> finalize")
    void pay_TwoPhaseTransactionOrder_VerifiedWithInOrder() {
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.empty());
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.findTop5ByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(Collections.emptyList());
        when(paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(Optional.empty());

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(50L);
            return p;
        });

        when(gateway.charge(any())).thenReturn(new GatewayResult(SimulatedOutcome.SUCCESS, null));
        when(paymentRepository.findByTransactionRef(anyString())).thenAnswer(inv -> Optional.of(Payment.builder().id(50L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(paymentRepository.findByIdForUpdate(50L)).thenAnswer(inv -> Optional.of(Payment.builder().id(50L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(shipmentService.createForPaidOrder(any())).thenReturn(Shipment.builder().id(1L).trackingCode("SIM-000009").status(ShipmentStatus.CREATED).build());

        service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null));

        InOrder inOrder = inOrder(orderRepository, paymentRepository, gateway);
        // TX1: lock order, save payment PENDING
        inOrder.verify(orderRepository).findByIdForUpdate(ORDER_ID);
        inOrder.verify(paymentRepository).save(any(Payment.class));
        // Outside TX: gateway charge
        inOrder.verify(gateway).charge(any(GatewayRequest.class));
        // TX2: lock order, lock payment, finalize
        inOrder.verify(orderRepository).findByIdForUpdate(ORDER_ID);
        inOrder.verify(paymentRepository).findByIdForUpdate(50L);
        inOrder.verify(paymentRepository).save(any(Payment.class));

        // When gateway returns PROCESSING -> response status is PENDING
        sampleOrder.setStatus(OrderStatus.PENDING_PAYMENT);
        when(gateway.charge(any())).thenReturn(new GatewayResult(SimulatedOutcome.PROCESSING, null));
        PaymentResponse procResp = service.pay(buyerUser, ORDER_ID, KEY_B, new PaymentRequest(PaymentMethod.MOCK_CARD, null));
        assertThat(procResp.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("UT-PAY-16 finalizePayment idempotent: Payment không còn PENDING thì bỏ qua, không tạo Shipment")
    void finalizePayment_Idempotent_WhenNotPending() {
        Payment paidPayment = Payment.builder()
                .id(60L)
                .orderId(ORDER_ID)
                .attemptNo(1)
                .status(PaymentStatus.SUCCESS)
                .transactionRef("TX-DONE")
                .build();
        when(paymentRepository.findByTransactionRef("TX-DONE")).thenReturn(Optional.of(paidPayment));
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(paidPayment));

        PaymentResponse response = service.finalizePayment("TX-DONE", SimulatedOutcome.SUCCESS, null);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(shipmentService, never()).createForPaidOrder(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-PAY-17 Timeout: Payment PENDING tạo NOW − 5m chuyển FAILED (TIMEOUT); NOW − 4m59s giữ PENDING")
    void timeoutStalePayments_TimesOutOnlyOlderThan5Minutes() {
        Payment stale5m = Payment.builder()
                .id(71L)
                .orderId(ORDER_ID)
                .status(PaymentStatus.PENDING)
                .createdAt(NOW.minusMinutes(5))
                .build();

        LocalDateTime cutoff = NOW.minusMinutes(5);
        when(paymentRepository.findByStatusAndCreatedAtLessThanEqual(PaymentStatus.PENDING, cutoff))
                .thenReturn(List.of(stale5m));

        mockOrderLock(sampleOrder);
        when(paymentRepository.findByIdForUpdate(71L)).thenReturn(Optional.of(stale5m));

        service.timeoutStalePayments();

        assertThat(stale5m.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(stale5m.getFailureReason()).isEqualTo("TIMEOUT");
        verify(paymentRepository).save(stale5m);
        verify(notificationService).notify(eq(BUYER_ID), eq(NotificationType.PAYMENT_RESULT), any(), any(), eq("ORDER"), eq(ORDER_ID));
    }

    @Test
    @DisplayName("UT-PAY-18 Kết quả muộn sau timeout bị bỏ qua, Order giữ PENDING_PAYMENT")
    void finalizePayment_LateResultAfterTimeout_Ignored() {
        Payment timedOutPayment = Payment.builder()
                .id(80L)
                .orderId(ORDER_ID)
                .attemptNo(1)
                .status(PaymentStatus.FAILED)
                .failureReason("TIMEOUT")
                .transactionRef("TX-LATE")
                .build();

        when(paymentRepository.findByTransactionRef("TX-LATE")).thenReturn(Optional.of(timedOutPayment));
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByIdForUpdate(80L)).thenReturn(Optional.of(timedOutPayment));

        PaymentResponse response = service.finalizePayment("TX-LATE", SimulatedOutcome.SUCCESS, null);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(shipmentService, never()).createForPaidOrder(any());
    }

    @Test
    @DisplayName("UT-PAY-19 Số tiền luôn lấy từ đơn hàng (final_price = 520000), không lấy từ client")
    void pay_AmountAlwaysTakenFromOrderFinalPrice() {
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.empty());
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.findTop5ByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(Collections.emptyList());
        when(paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(Optional.empty());

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        when(paymentRepository.save(paymentCaptor.capture())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(90L);
            return p;
        });

        ArgumentCaptor<GatewayRequest> gwReqCaptor = ArgumentCaptor.forClass(GatewayRequest.class);
        when(gateway.charge(gwReqCaptor.capture())).thenReturn(new GatewayResult(SimulatedOutcome.SUCCESS, null));
        when(paymentRepository.findByTransactionRef(anyString())).thenAnswer(inv -> Optional.of(Payment.builder().id(90L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(paymentRepository.findByIdForUpdate(90L)).thenAnswer(inv -> Optional.of(Payment.builder().id(90L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(shipmentService.createForPaidOrder(any())).thenReturn(Shipment.builder().id(1L).trackingCode("SIM-000009").status(ShipmentStatus.CREATED).build());

        service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null));

        assertThat(paymentCaptor.getAllValues().get(0).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(520000));
        assertThat(gwReqCaptor.getValue().amount()).isEqualByComparingTo(BigDecimal.valueOf(520000));
    }

    @Test
    @DisplayName("UT-PAY-20 simulateResult khi allow-simulate-result=true truyền hint vào GatewayRequest")
    void pay_AllowSimulateResultTrue_PassesHint() {
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.empty());
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.findTop5ByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(Collections.emptyList());
        when(paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(91L);
            return p;
        });

        ArgumentCaptor<GatewayRequest> gwReqCaptor = ArgumentCaptor.forClass(GatewayRequest.class);
        when(gateway.charge(gwReqCaptor.capture())).thenReturn(new GatewayResult(SimulatedOutcome.FAILED, "MOCK_DECLINED"));
        when(paymentRepository.findByTransactionRef(anyString())).thenAnswer(inv -> Optional.of(Payment.builder().id(91L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(paymentRepository.findByIdForUpdate(91L)).thenAnswer(inv -> Optional.of(Payment.builder().id(91L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));

        paymentProperties.getMock().setAllowSimulateResult(true);
        service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, SimulatedOutcome.FAILED));

        assertThat(gwReqCaptor.getValue().hint()).isEqualTo(SimulatedOutcome.FAILED);
    }

    @Test
    @DisplayName("UT-PAY-21 simulateResult khi allow-simulate-result=false bỏ qua và truyền hint = null")
    void pay_AllowSimulateResultFalse_HintIsNull() {
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.empty());
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.findTop5ByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(Collections.emptyList());
        when(paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(92L);
            return p;
        });

        ArgumentCaptor<GatewayRequest> gwReqCaptor = ArgumentCaptor.forClass(GatewayRequest.class);
        when(gateway.charge(gwReqCaptor.capture())).thenReturn(new GatewayResult(SimulatedOutcome.SUCCESS, null));
        when(paymentRepository.findByTransactionRef(anyString())).thenAnswer(inv -> Optional.of(Payment.builder().id(92L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(paymentRepository.findByIdForUpdate(92L)).thenAnswer(inv -> Optional.of(Payment.builder().id(92L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(shipmentService.createForPaidOrder(any())).thenReturn(Shipment.builder().id(1L).trackingCode("SIM-000009").status(ShipmentStatus.CREATED).build());

        paymentProperties.getMock().setAllowSimulateResult(false);
        service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, SimulatedOutcome.FAILED));

        assertThat(gwReqCaptor.getValue().hint()).isNull();
    }

    @Test
    @DisplayName("UT-PAY-22 Đơn bị hủy khi đang xử lý: finalizePayment SUCCESS không chuyển Order sang PAID")
    void pay_OrderCancelledWhileProcessing_FinalizeSuccessDoesNotChangeOrderToPaid() {
        Payment pendingPayment = Payment.builder()
                .id(93L)
                .orderId(ORDER_ID)
                .attemptNo(1)
                .status(PaymentStatus.PENDING)
                .transactionRef("TX-CANCELLED")
                .build();
        when(paymentRepository.findByTransactionRef("TX-CANCELLED")).thenReturn(Optional.of(pendingPayment));

        sampleOrder.setStatus(OrderStatus.CANCELLED);
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByIdForUpdate(93L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = service.finalizePayment("TX-CANCELLED", SimulatedOutcome.SUCCESS, null);

        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(shipmentService, never()).createForPaidOrder(any());
    }

    @Test
    @DisplayName("UT-PAY-23 Lịch sử thanh toán: BUYER và ADMIN nhận danh sách attempt_no giảm dần; SELLER nhận 403")
    void getPaymentsByOrder_AuthorizationAndOrdering() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(sampleOrder));

        Payment p1 = Payment.builder().id(1L).orderId(ORDER_ID).attemptNo(1).method(PaymentMethod.MOCK_CARD).amount(BigDecimal.valueOf(520000)).status(PaymentStatus.FAILED).transactionRef("TX-1").createdAt(NOW.minusMinutes(5)).build();
        Payment p2 = Payment.builder().id(2L).orderId(ORDER_ID).attemptNo(2).method(PaymentMethod.MOCK_BANK).amount(BigDecimal.valueOf(520000)).status(PaymentStatus.SUCCESS).transactionRef("TX-2").createdAt(NOW.minusMinutes(1)).build();

        when(paymentRepository.findByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(List.of(p2, p1));

        // BUYER sees list ordered by attemptNo DESC
        List<PaymentHistoryResponse> buyerHistory = service.getPaymentsByOrder(buyerUser, ORDER_ID);
        assertThat(buyerHistory).hasSize(2);
        assertThat(buyerHistory.get(0).getAttemptNo()).isEqualTo(2);
        assertThat(buyerHistory.get(1).getAttemptNo()).isEqualTo(1);

        // ADMIN also sees list
        List<PaymentHistoryResponse> adminHistory = service.getPaymentsByOrder(adminUser, ORDER_ID);
        assertThat(adminHistory).hasSize(2);

        // SELLER receives 403 FORBIDDEN
        assertThatThrownBy(() -> service.getPaymentsByOrder(sellerUser, ORDER_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("FORBIDDEN"));
    }

    @Test
    @DisplayName("UT-PAY-24 Thua cuộc đua ở DB (DataIntegrityViolationException) quy về 409 PAYMENT_IN_PROGRESS không ném 500")
    void pay_DataIntegrityViolationOnInsert_ReturnsPaymentInProgress() {
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.empty());
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.findTop5ByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(Collections.emptyList());
        when(paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(Optional.empty());

        when(paymentRepository.save(any(Payment.class))).thenThrow(new DataIntegrityViolationException("uq_payments_one_pending"));

        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getCode()).isEqualTo("PAYMENT_IN_PROGRESS");
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    @DisplayName("UT-PAY-25 transaction_ref duy nhất với tiền tố MOCK-")
    void pay_TransactionRefUniqueWithMockPrefix() {
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(eq(ORDER_ID), anyString())).thenReturn(Optional.empty());
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.findTop5ByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(Collections.emptyList());
        when(paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(Optional.empty());

        List<String> refs = new ArrayList<>();
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getTransactionRef() != null && !refs.contains(p.getTransactionRef())) {
                refs.add(p.getTransactionRef());
            }
            p.setId(99L);
            return p;
        });

        when(gateway.charge(any())).thenReturn(new GatewayResult(SimulatedOutcome.SUCCESS, null));
        when(paymentRepository.findByTransactionRef(anyString())).thenAnswer(inv -> Optional.of(Payment.builder().id(99L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(paymentRepository.findByIdForUpdate(99L)).thenAnswer(inv -> Optional.of(Payment.builder().id(99L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(shipmentService.createForPaidOrder(any())).thenReturn(Shipment.builder().id(1L).trackingCode("SIM-000009").status(ShipmentStatus.CREATED).build());

        service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null));
        sampleOrder.setStatus(OrderStatus.PENDING_PAYMENT);
        service.pay(buyerUser, ORDER_ID, KEY_B, new PaymentRequest(PaymentMethod.MOCK_CARD, null));

        assertThat(refs).hasSize(2);
        assertThat(refs.get(0)).startsWith("MOCK-");
        assertThat(refs.get(1)).startsWith("MOCK-");
        assertThat(refs.get(0)).isNotEqualTo(refs.get(1));
    }

    @Test
    @DisplayName("pay: order not found, blank address/phone, active user, null deadline, existing success with shipment")
    void testPayAdditionalBranches() {
        // 1. Order not found
        when(orderRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.pay(buyerUser, 999L, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class).matches(e -> ((AppException) e).getCode().equals("NOT_FOUND"));

        // 2. Blank address & phone
        Order blankAddressOrder = Order.builder().id(ORDER_ID).buyerId(BUYER_ID).status(OrderStatus.PENDING_PAYMENT)
                .shippingAddress("   ").phoneNumber("0901234567").build();
        mockOrderLock(blankAddressOrder);
        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class).matches(e -> ((AppException) e).getCode().equals("VALIDATION_ERROR"));

        Order blankPhoneOrder = Order.builder().id(ORDER_ID).buyerId(BUYER_ID).status(OrderStatus.PENDING_PAYMENT)
                .shippingAddress("123 Street").phoneNumber("   ").build();
        mockOrderLock(blankPhoneOrder);
        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class).matches(e -> ((AppException) e).getCode().equals("VALIDATION_ERROR"));

        // 3. Existing SUCCESS with shipment
        mockOrderLock(sampleOrder);
        Payment existingSuccess = Payment.builder().id(12L).orderId(ORDER_ID).status(PaymentStatus.SUCCESS).idempotencyKey(KEY_A).attemptNo(1).build();
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.of(existingSuccess));
        Shipment existingShipment = Shipment.builder().id(55L).trackingCode("SIM-000009").status(ShipmentStatus.CREATED).build();
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existingShipment));

        PaymentResponse earlyRes = service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null));
        assertThat(earlyRes.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(earlyRes.getShipment()).isNotNull();
        assertThat(earlyRes.getShipment().getTrackingCode()).isEqualTo("SIM-000009");
    }

    @Test
    @DisplayName("finalizePayment: not found, expired order, processing, null failure reason, idempotent with shipment")
    void testFinalizePaymentAdditionalBranches() {
        // 1. paymentLookup isEmpty
        when(paymentRepository.findByTransactionRef("NON-EXISTENT")).thenReturn(Optional.empty());
        assertThat(service.finalizePayment("NON-EXISTENT", SimulatedOutcome.SUCCESS, null)).isNull();

        // 2. paymentOpt isEmpty
        Payment p = Payment.builder().id(88L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build();
        when(paymentRepository.findByTransactionRef("TX-OPT-EMPTY")).thenReturn(Optional.of(p));
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByIdForUpdate(88L)).thenReturn(Optional.empty());
        assertThat(service.finalizePayment("TX-OPT-EMPTY", SimulatedOutcome.SUCCESS, null)).isNull();

        // 3. order EXPIRED while in-flight
        when(paymentRepository.findByTransactionRef("TX-EXPIRED")).thenReturn(Optional.of(p));
        when(paymentRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(p));
        Order expiredOrder = Order.builder().id(ORDER_ID).status(OrderStatus.EXPIRED).build();
        mockOrderLock(expiredOrder);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponse resExpired = service.finalizePayment("TX-EXPIRED", SimulatedOutcome.SUCCESS, null);
        assertThat(resExpired.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(resExpired.getFailureReason()).isEqualTo("ORDER_EXPIRED");

        // 4. outcome PROCESSING
        p.setStatus(PaymentStatus.PENDING);
        mockOrderLock(sampleOrder);
        PaymentResponse resProcessing = service.finalizePayment("TX-EXPIRED", SimulatedOutcome.PROCESSING, null);
        assertThat(resProcessing.getStatus()).isEqualTo(PaymentStatus.PENDING);

        // 5. outcome FAILED with null failureReason -> defaults to MOCK_DECLINED
        p.setStatus(PaymentStatus.PENDING);
        PaymentResponse resFailedNullReason = service.finalizePayment("TX-EXPIRED", SimulatedOutcome.FAILED, null);
        assertThat(resFailedNullReason.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(resFailedNullReason.getFailureReason()).isEqualTo("MOCK_DECLINED");

        // 6. Idempotent SUCCESS with existing shipment
        Payment alreadySuccess = Payment.builder().id(88L).orderId(ORDER_ID).status(PaymentStatus.SUCCESS).attemptNo(1).build();
        when(paymentRepository.findByTransactionRef("TX-ALREADY-SUCCESS")).thenReturn(Optional.of(alreadySuccess));
        when(paymentRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(alreadySuccess));
        Shipment existingShipment = Shipment.builder().id(55L).trackingCode("SIM-000009").status(ShipmentStatus.CREATED).build();
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existingShipment));

        PaymentResponse resIdempotentSuccess = service.finalizePayment("TX-ALREADY-SUCCESS", SimulatedOutcome.SUCCESS, null);
        assertThat(resIdempotentSuccess.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(resIdempotentSuccess.getShipment()).isNotNull();
    }

    @Test
    @DisplayName("getPaymentsByOrder not found and timeoutStalePayments null order branch")
    void testGetPaymentsAndTimeoutBranches() {
        // getPaymentsByOrder not found
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPaymentsByOrder(buyerUser, 999L))
                .isInstanceOf(AppException.class).matches(e -> ((AppException) e).getCode().equals("NOT_FOUND"));

        // timeoutStalePayments with null order
        Payment stale = Payment.builder().id(77L).orderId(ORDER_ID).status(PaymentStatus.PENDING).createdAt(NOW.minusMinutes(10)).build();
        when(paymentRepository.findByStatusAndCreatedAtLessThanEqual(eq(PaymentStatus.PENDING), any()))
                .thenReturn(List.of(stale));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentRepository.findByIdForUpdate(77L)).thenReturn(Optional.of(stale));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        service.timeoutStalePayments();
        assertThat(stale.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(stale.getFailureReason()).isEqualTo("TIMEOUT");
    }

    @Test
    @DisplayName("Payment edge branches: null phone, rate limit null createdAt, finalize success on non-pending order, minimal constructor")
    void testPaymentEdgeBranches() {
        // 1. null phone
        Order nullPhone = Order.builder().id(ORDER_ID).buyerId(BUYER_ID).status(OrderStatus.PENDING_PAYMENT)
                .shippingAddress("123 Street").phoneNumber(null).build();
        mockOrderLock(nullPhone);
        assertThatThrownBy(() -> service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null)))
                .isInstanceOf(AppException.class).matches(e -> ((AppException) e).getCode().equals("VALIDATION_ERROR"));

        // 2. rate limit oldestOf5.getCreatedAt() == null
        mockOrderLock(sampleOrder);
        Payment pNoCreated = Payment.builder().id(1L).createdAt(null).build();
        List<Payment> fiveWithNull = new ArrayList<>(Collections.nCopies(5, pNoCreated));
        when(paymentRepository.findTop5ByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(fiveWithNull);
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, KEY_A)).thenReturn(Optional.empty());
        when(paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(100L);
            return p;
        });
        when(gateway.charge(any())).thenReturn(new GatewayResult(SimulatedOutcome.SUCCESS, null));
        when(paymentRepository.findByTransactionRef(anyString())).thenAnswer(i -> Optional.of(Payment.builder().id(100L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(paymentRepository.findByIdForUpdate(100L)).thenAnswer(i -> Optional.of(Payment.builder().id(100L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build()));
        when(shipmentService.createForPaidOrder(any())).thenReturn(Shipment.builder().id(1L).trackingCode("SIM-000009").status(ShipmentStatus.CREATED).build());

        PaymentResponse resRateLimitNull = service.pay(buyerUser, ORDER_ID, KEY_A, new PaymentRequest(PaymentMethod.MOCK_CARD, null));
        assertThat(resRateLimitNull.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // 3. finalizePayment on order that is already PAID
        Payment pendingPay = Payment.builder().id(101L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build();
        when(paymentRepository.findByTransactionRef("TX-ORDER-ALREADY-PAID")).thenReturn(Optional.of(pendingPay));
        when(paymentRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(pendingPay));
        Order alreadyPaidOrder = Order.builder().id(ORDER_ID).status(OrderStatus.PAID).build();
        mockOrderLock(alreadyPaidOrder);
        PaymentResponse resAlreadyPaid = service.finalizePayment("TX-ORDER-ALREADY-PAID", SimulatedOutcome.SUCCESS, null);
        assertThat(resAlreadyPaid.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // 4. timeoutStalePayments when paymentOpt is empty or status != PENDING
        Payment stale2 = Payment.builder().id(78L).orderId(ORDER_ID).status(PaymentStatus.PENDING).createdAt(NOW.minusMinutes(10)).build();
        when(paymentRepository.findByStatusAndCreatedAtLessThanEqual(eq(PaymentStatus.PENDING), any()))
                .thenReturn(List.of(stale2));
        when(paymentRepository.findByIdForUpdate(78L)).thenReturn(Optional.empty());
        service.timeoutStalePayments(); // doesn't fail

        // 5. minimal constructor (null userRepository & shipmentRepository)
        PaymentServiceImpl minimalService = new PaymentServiceImpl(
                orderRepository, paymentRepository, gateway, shipmentService, null, notificationService,
                new DirectTransactionTemplate(), paymentProperties, clock, null
        );
        when(paymentRepository.findByTransactionRef("TX-MINIMAL")).thenReturn(Optional.of(pendingPay));
        mockOrderLock(sampleOrder);
        sampleOrder.setStatus(OrderStatus.PENDING_PAYMENT);
        PaymentResponse minRes = minimalService.finalizePayment("TX-MINIMAL", SimulatedOutcome.SUCCESS, null);
        assertThat(minRes.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("Payment.createdAt: khi tạo Payment mới, createdAt luôn được gán bằng LocalDateTime.now(clock)")
    void pay_SetsCreatedAtFromClock() {
        mockOrderLock(sampleOrder);
        when(paymentRepository.findByOrderIdAndIdempotencyKey(ORDER_ID, "VALID-KEY-12345")).thenReturn(Optional.empty());
        when(paymentRepository.existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.PENDING)).thenReturn(false);
        when(paymentRepository.findTop5ByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(Collections.emptyList());
        when(paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(Optional.empty());

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        when(paymentRepository.save(paymentCaptor.capture())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(99L);
            return p;
        });

        when(gateway.charge(any(GatewayRequest.class))).thenReturn(new GatewayResult(SimulatedOutcome.SUCCESS, null));
        when(paymentRepository.findByTransactionRef(anyString())).thenAnswer(inv -> {
            Payment p = Payment.builder()
                    .id(99L)
                    .orderId(ORDER_ID)
                    .attemptNo(1)
                    .amount(sampleOrder.getFinalPrice())
                    .status(PaymentStatus.PENDING)
                    .transactionRef(inv.getArgument(0))
                    .build();
            return Optional.of(p);
        });
        when(paymentRepository.findByIdForUpdate(99L)).thenAnswer(inv -> {
            Payment p = Payment.builder()
                    .id(99L)
                    .orderId(ORDER_ID)
                    .attemptNo(1)
                    .amount(sampleOrder.getFinalPrice())
                    .status(PaymentStatus.PENDING)
                    .transactionRef("MOCK-TX")
                    .build();
            return Optional.of(p);
        });

        Shipment shipment = Shipment.builder().id(4L).orderId(ORDER_ID).trackingCode("SIM-000009").status(ShipmentStatus.CREATED).build();
        when(shipmentService.createForPaidOrder(sampleOrder)).thenReturn(shipment);

        service.pay(buyerUser, ORDER_ID, "VALID-KEY-12345", new PaymentRequest(PaymentMethod.MOCK_CARD, null));

        Payment createdPayment = paymentCaptor.getAllValues().get(0);
        assertThat(createdPayment.getCreatedAt()).isNotNull();
        assertThat(createdPayment.getCreatedAt()).isEqualTo(LocalDateTime.now(clock));
    }
}
