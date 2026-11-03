package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.payment.PaymentHistoryResponse;
import com.omnibid.dto.payment.PaymentRequest;
import com.omnibid.dto.payment.PaymentResponse;
import com.omnibid.dto.payment.ShipmentSummaryDto;
import com.omnibid.entity.Order;
import com.omnibid.entity.Payment;
import com.omnibid.entity.Shipment;
import com.omnibid.enums.*;
import com.omnibid.gateway.MockPaymentGateway;
import com.omnibid.gateway.MockPaymentGateway.GatewayRequest;
import com.omnibid.gateway.MockPaymentGateway.GatewayResult;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.PaymentRepository;
import com.omnibid.repository.ShipmentRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.NotificationService;
import com.omnibid.service.PaymentService;
import com.omnibid.service.ShipmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final MockPaymentGateway gateway;
    private final ShipmentService shipmentService;
    private final ShipmentRepository shipmentRepository;
    private final NotificationService notificationService;
    private final TransactionOperations transactionOperations;
    private final AppProperties.PaymentProperties paymentProperties;
    private final Clock clock;
    private UserRepository userRepository;

    @Autowired
    public PaymentServiceImpl(OrderRepository orderRepository,
                              PaymentRepository paymentRepository,
                              MockPaymentGateway gateway,
                              ShipmentService shipmentService,
                              ShipmentRepository shipmentRepository,
                              NotificationService notificationService,
                              PlatformTransactionManager transactionManager,
                              AppProperties appProperties,
                              Clock clock,
                              UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.shipmentService = shipmentService;
        this.shipmentRepository = shipmentRepository;
        this.notificationService = notificationService;
        this.transactionOperations = new TransactionTemplate(transactionManager);
        this.paymentProperties = appProperties.getPayment();
        this.clock = clock;
        this.userRepository = userRepository;
    }

    public PaymentServiceImpl(OrderRepository orderRepository,
                              PaymentRepository paymentRepository,
                              MockPaymentGateway gateway,
                              ShipmentService shipmentService,
                              NotificationService notificationService,
                              TransactionOperations transactionOperations,
                              AppProperties.PaymentProperties paymentProperties,
                              Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.shipmentService = shipmentService;
        this.shipmentRepository = null;
        this.notificationService = notificationService;
        this.transactionOperations = transactionOperations;
        this.paymentProperties = paymentProperties;
        this.clock = clock;
        this.userRepository = null;
    }

    public PaymentServiceImpl(OrderRepository orderRepository,
                              PaymentRepository paymentRepository,
                              MockPaymentGateway gateway,
                              ShipmentService shipmentService,
                              NotificationService notificationService,
                              TransactionOperations transactionOperations,
                              AppProperties.PaymentProperties paymentProperties,
                              Clock clock,
                              UserRepository userRepository) {
        this(orderRepository, paymentRepository, gateway, shipmentService, notificationService, transactionOperations, paymentProperties, clock);
        this.userRepository = userRepository;
    }

    public PaymentServiceImpl(OrderRepository orderRepository,
                              PaymentRepository paymentRepository,
                              MockPaymentGateway gateway,
                              ShipmentService shipmentService,
                              ShipmentRepository shipmentRepository,
                              NotificationService notificationService,
                              TransactionOperations transactionOperations,
                              AppProperties.PaymentProperties paymentProperties,
                              Clock clock,
                              UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.shipmentService = shipmentService;
        this.shipmentRepository = shipmentRepository;
        this.notificationService = notificationService;
        this.transactionOperations = transactionOperations;
        this.paymentProperties = paymentProperties;
        this.clock = clock;
        this.userRepository = userRepository;
    }

    private record Tx1Result(Payment payment, PaymentResponse earlyResponse) {}

    @Override
    public PaymentResponse pay(CurrentUser currentUser, Long orderId, String idempotencyKey, PaymentRequest request) {
        // TX1: Lock order, validate, insert PENDING payment, commit
        Tx1Result tx1 = transactionOperations.execute(status -> {
            // 1. Order exists
            Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(
                    () -> new AppException("NOT_FOUND", "Không tìm thấy đơn hàng", HttpStatus.NOT_FOUND)
            );

            // 2. Caller is buyer
            if (!order.getBuyerId().equals(currentUser.id())) {
                throw new AppException("FORBIDDEN", "Chỉ người mua mới được phép thanh toán", HttpStatus.FORBIDDEN);
            }

            // 3. Header valid
            if (idempotencyKey == null || !idempotencyKey.matches("^[A-Za-z0-9_-]{8,64}$")) {
                throw new AppException("VALIDATION_ERROR", "Idempotency-Key không hợp lệ", HttpStatus.BAD_REQUEST);
            }

            // 4. Key already exists for this order -> return old result (PAY-05)
            Optional<Payment> existingOpt = paymentRepository.findByOrderIdAndIdempotencyKey(orderId, idempotencyKey);
            if (existingOpt.isPresent()) {
                Payment existing = existingOpt.get();
                ShipmentSummaryDto shipmentDto = null;
                if (existing.getStatus() == PaymentStatus.SUCCESS && shipmentRepository != null) {
                    shipmentDto = shipmentRepository.findByOrderId(order.getId())
                            .map(s -> new ShipmentSummaryDto(s.getId(), s.getTrackingCode(), s.getStatus()))
                            .orElse(null);
                }
                PaymentResponse early = PaymentResponse.builder()
                        .paymentId(existing.getId())
                        .attemptNo(existing.getAttemptNo())
                        .status(existing.getStatus())
                        .orderStatus(order.getStatus())
                        .failureReason(existing.getFailureReason())
                        .shipment(shipmentDto)
                        .build();
                return new Tx1Result(null, early);
            }

            // 5. Buyer ACTIVE
            if (userRepository != null) {
                userRepository.findById(currentUser.id()).ifPresent(user -> {
                    if (user.getStatus() == UserStatus.BANNED) {
                        throw new AppException("ACCOUNT_BANNED", "Tài khoản của bạn đã bị khóa", HttpStatus.FORBIDDEN);
                    }
                });
            }

            // 6. Order CANCELLED
            if (order.getStatus() == OrderStatus.CANCELLED) {
                throw new AppException("ORDER_CANCELLED", "Đơn hàng đã bị hủy", HttpStatus.CONFLICT);
            }

            // 7. Order EXPIRED
            if (order.getStatus() == OrderStatus.EXPIRED) {
                throw new AppException("ORDER_EXPIRED", "Đơn hàng đã hết hạn", HttpStatus.CONFLICT);
            }

            // 8. Order is not PENDING_PAYMENT
            if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                throw new AppException("INVALID_STATUS_TRANSITION", "Đơn hàng không ở trạng thái chờ thanh toán", HttpStatus.CONFLICT);
            }

            // 9. now < payment_deadline
            LocalDateTime now = LocalDateTime.now(clock);
            if (order.getPaymentDeadline() != null && !now.isBefore(order.getPaymentDeadline())) {
                throw new AppException("ORDER_EXPIRED", "Đơn hàng đã quá hạn thanh toán", HttpStatus.CONFLICT);
            }

            // 10. Order has shipping_address and phone_number
            if (order.getShippingAddress() == null || order.getShippingAddress().isBlank()
                    || order.getPhoneNumber() == null || order.getPhoneNumber().isBlank()) {
                throw new AppException("VALIDATION_ERROR", "Đơn hàng chưa có địa chỉ giao hàng hoặc số điện thoại", HttpStatus.BAD_REQUEST);
            }

            // 11. No Payment currently PENDING
            if (paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING)) {
                throw new AppException("PAYMENT_IN_PROGRESS", "Đang có giao dịch thanh toán chờ xử lý", HttpStatus.CONFLICT);
            }

            // 12. Rate limit: count payments created within last 10 minutes < 5
            List<Payment> recentPayments = paymentRepository.findTop5ByOrderIdOrderByCreatedAtDesc(order.getId());
            if (recentPayments.size() >= paymentProperties.getRateLimit().getMax()) {
                Payment oldestOf5 = recentPayments.get(recentPayments.size() - 1);
                if (oldestOf5.getCreatedAt() != null) {
                    LocalDateTime windowExpiry = oldestOf5.getCreatedAt().plusMinutes(paymentProperties.getRateLimit().getWindowMinutes());
                    if (now.isBefore(windowExpiry)) {
                        long diffSeconds = Duration.between(now, windowExpiry).getSeconds();
                        long retryAfterSeconds = Math.max(1, diffSeconds);
                        throw new AppException("PAYMENT_RATE_LIMITED", "Vượt quá giới hạn số lần thanh toán trong 10 phút",
                                HttpStatus.TOO_MANY_REQUESTS, Map.of("retryAfterSeconds", retryAfterSeconds));
                    }
                }
            }

            // Create Payment PENDING
            int attemptNo = paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(order.getId())
                    .map(p -> p.getAttemptNo() + 1)
                    .orElse(1);

            BigDecimal amount = order.getFinalPrice();
            String txRef = "MOCK-" + UUID.randomUUID();

            Payment payment = Payment.builder()
                    .orderId(order.getId())
                    .attemptNo(attemptNo)
                    .amount(amount)
                    .method(request.getMethod())
                    .status(PaymentStatus.PENDING)
                    .idempotencyKey(idempotencyKey)
                    .transactionRef(txRef)
                    .createdAt(now)
                    .build();

            try {
                Payment saved = paymentRepository.save(payment);
                return new Tx1Result(saved, null);
            } catch (DataIntegrityViolationException dive) {
                log.warn("Data integrity violation when inserting payment for orderId={}: {}", order.getId(), dive.getMessage());
                throw new AppException("PAYMENT_IN_PROGRESS", "Đang có giao dịch thanh toán chờ xử lý", HttpStatus.CONFLICT);
            }
        });

        if (tx1.earlyResponse() != null) {
            return tx1.earlyResponse();
        }

        Payment createdPayment = tx1.payment();

        // Call gateway outside transaction
        SimulatedOutcome hint = null;
        if (paymentProperties.getMock().isAllowSimulateResult()) {
            hint = request.getSimulateResult();
        }

        GatewayRequest gwRequest = new GatewayRequest(
                createdPayment.getTransactionRef(),
                createdPayment.getAmount(),
                createdPayment.getMethod(),
                hint
        );

        GatewayResult gwResult = gateway.charge(gwRequest);

        // TX2: Lock order, lock payment, finalize
        return finalizePayment(createdPayment.getTransactionRef(), gwResult.outcome(), gwResult.failureReason());
    }

    @Override
    public PaymentResponse finalizePayment(String transactionRef, SimulatedOutcome outcome, String failureReason) {
        return transactionOperations.execute(status -> {
            Optional<Payment> paymentLookup = paymentRepository.findByTransactionRef(transactionRef);
            if (paymentLookup.isEmpty()) {
                log.warn("Payment not found for transactionRef: {}", transactionRef);
                return null;
            }
            Payment initial = paymentLookup.get();

            // Strict lock order: Lock Order first, then lock Payment
            Order order = orderRepository.findByIdForUpdate(initial.getOrderId()).orElseThrow(
                    () -> new AppException("NOT_FOUND", "Không tìm thấy đơn hàng", HttpStatus.NOT_FOUND)
            );

            Optional<Payment> paymentOpt = paymentRepository.findByIdForUpdate(initial.getId());
            if (paymentOpt.isEmpty()) {
                log.warn("Payment not found for update: id={}", initial.getId());
                return null;
            }
            Payment payment = paymentOpt.get();

            // Idempotent: If not PENDING, skip modification
            if (payment.getStatus() != PaymentStatus.PENDING) {
                log.warn("Payment {} is no longer PENDING (status={})", transactionRef, payment.getStatus());
                ShipmentSummaryDto shipmentDto = null;
                if (payment.getStatus() == PaymentStatus.SUCCESS && shipmentRepository != null) {
                    shipmentDto = shipmentRepository.findByOrderId(order.getId())
                            .map(s -> new ShipmentSummaryDto(s.getId(), s.getTrackingCode(), s.getStatus()))
                            .orElse(null);
                }
                return PaymentResponse.builder()
                        .paymentId(payment.getId())
                        .attemptNo(payment.getAttemptNo())
                        .status(payment.getStatus())
                        .orderStatus(order.getStatus())
                        .failureReason(payment.getFailureReason())
                        .shipment(shipmentDto)
                        .build();
            }

            LocalDateTime now = LocalDateTime.now(clock);

            if (outcome == SimulatedOutcome.SUCCESS) {
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setPaidAt(now);
                payment = paymentRepository.save(payment);

                ShipmentSummaryDto shipmentDto = null;
                // If order is PENDING_PAYMENT, update to PAID and create Shipment
                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    order.markPaid(now);
                    orderRepository.save(order);

                    Shipment shipment = shipmentService.createForPaidOrder(order);
                    shipmentDto = new ShipmentSummaryDto(shipment.getId(), shipment.getTrackingCode(), shipment.getStatus());

                    // Notify buyer and seller
                    notificationService.notify(order.getBuyerId(), NotificationType.PAYMENT_RESULT,
                            NotificationTemplates.paymentSuccessBuyerTitle(),
                            NotificationTemplates.paymentSuccessBuyerContent(order.getId(), shipment.getTrackingCode()),
                            "ORDER", order.getId());

                    notificationService.notify(order.getSellerId(), NotificationType.PAYMENT_RESULT,
                            NotificationTemplates.paymentSuccessSellerTitle(order.getId()),
                            NotificationTemplates.paymentSuccessSellerContent(order.getId()),
                            "ORDER", order.getId());
                }

                return PaymentResponse.builder()
                        .paymentId(payment.getId())
                        .attemptNo(payment.getAttemptNo())
                        .status(PaymentStatus.SUCCESS)
                        .orderStatus(order.getStatus())
                        .shipment(shipmentDto)
                        .build();

            } else if (outcome == SimulatedOutcome.FAILED) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(failureReason != null ? failureReason : "MOCK_DECLINED");
                payment = paymentRepository.save(payment);

                notificationService.notify(order.getBuyerId(), NotificationType.PAYMENT_RESULT,
                        NotificationTemplates.paymentFailedTitle(payment.getFailureReason()),
                        NotificationTemplates.paymentFailedContent(order.getId(), payment.getFailureReason()),
                        "ORDER", order.getId());

                return PaymentResponse.builder()
                        .paymentId(payment.getId())
                        .attemptNo(payment.getAttemptNo())
                        .status(PaymentStatus.FAILED)
                        .orderStatus(order.getStatus())
                        .failureReason(payment.getFailureReason())
                        .build();

            } else { // PROCESSING
                return PaymentResponse.builder()
                        .paymentId(payment.getId())
                        .attemptNo(payment.getAttemptNo())
                        .status(PaymentStatus.PENDING)
                        .orderStatus(order.getStatus())
                        .build();
            }
        });
    }

    @Override
    public List<PaymentHistoryResponse> getPaymentsByOrder(CurrentUser currentUser, Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new AppException("NOT_FOUND", "Không tìm thấy đơn hàng", HttpStatus.NOT_FOUND)
        );

        boolean isBuyer = order.getBuyerId().equals(currentUser.id());
        boolean isAdmin = currentUser.isAdmin();
        if (!isBuyer && !isAdmin) {
            throw new AppException("FORBIDDEN", "Bạn không có quyền xem lịch sử thanh toán của đơn này", HttpStatus.FORBIDDEN);
        }

        List<Payment> payments = paymentRepository.findByOrderIdOrderByAttemptNoDesc(orderId);
        return payments.stream()
                .map(p -> PaymentHistoryResponse.builder()
                        .paymentId(p.getId())
                        .attemptNo(p.getAttemptNo())
                        .method(p.getMethod())
                        .amount(p.getAmount())
                        .status(p.getStatus())
                        .transactionRef(p.getTransactionRef())
                        .failureReason(p.getFailureReason())
                        .paidAt(p.getPaidAt())
                        .createdAt(p.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void timeoutStalePayments() {
        int timeoutMinutes = paymentProperties.getPendingTimeoutMinutes();
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(timeoutMinutes);
        List<Payment> stalePayments = paymentRepository.findByStatusAndCreatedAtLessThanEqual(PaymentStatus.PENDING, cutoff);

        for (Payment p : stalePayments) {
            try {
                transactionOperations.execute(status -> {
                    Order order = orderRepository.findByIdForUpdate(p.getOrderId()).orElse(null);
                    Payment payment = paymentRepository.findByIdForUpdate(p.getId()).orElse(null);
                    if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
                        payment.setStatus(PaymentStatus.FAILED);
                        payment.setFailureReason("TIMEOUT");
                        paymentRepository.save(payment);

                        if (order != null) {
                            notificationService.notify(order.getBuyerId(), NotificationType.PAYMENT_RESULT,
                                    NotificationTemplates.paymentFailedTitle("TIMEOUT"),
                                    NotificationTemplates.paymentFailedContent(order.getId(), "TIMEOUT"),
                                    "ORDER", order.getId());
                        }
                    }
                    return null;
                });
            } catch (Exception ex) {
                log.error("Failed to timeout stale payment id={}", p.getId(), ex);
            }
        }
    }
}
