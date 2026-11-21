package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.payment.PaymentHistoryResponse;
import com.omnibid.dto.payment.PaymentRequest;
import com.omnibid.dto.payment.PaymentResponse;
import com.omnibid.entity.Order;
import com.omnibid.entity.Payment;
import com.omnibid.enums.*;
import com.omnibid.gateway.MockPaymentGateway;
import com.omnibid.gateway.MockPaymentGateway.GatewayRequest;
import com.omnibid.gateway.MockPaymentGateway.GatewayResult;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.PaymentRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.NotificationService;
import com.omnibid.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final MockPaymentGateway gateway;
    private final NotificationService notificationService;
    private final TransactionOperations transactionOperations;
    private final AppProperties.Payment paymentProperties;
    private final Clock clock;
    private final UserRepository userRepository;

    @Autowired
    public PaymentServiceImpl(OrderRepository orderRepository,
                              PaymentRepository paymentRepository,
                              MockPaymentGateway gateway,
                              NotificationService notificationService,
                              PlatformTransactionManager transactionManager,
                              AppProperties appProperties,
                              Clock clock,
                              UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.notificationService = notificationService;
        this.transactionOperations = new TransactionTemplate(transactionManager);
        this.paymentProperties = appProperties.getPayment();
        this.clock = clock;
        this.userRepository = userRepository;
    }

    public PaymentServiceImpl(OrderRepository orderRepository,
                              PaymentRepository paymentRepository,
                              MockPaymentGateway gateway,
                              NotificationService notificationService,
                              TransactionOperations transactionOperations,
                              AppProperties.Payment paymentProperties,
                              Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.notificationService = notificationService;
        this.transactionOperations = transactionOperations;
        this.paymentProperties = paymentProperties;
        this.clock = clock;
        this.userRepository = null;
    }

    @Override
    public PaymentResponse pay(CurrentUser currentUser, Long orderId, String idempotencyKey, PaymentRequest request) {
        return transactionOperations.execute(status -> {
            Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(
                    () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng"));

            if (!order.getBuyerId().equals(currentUser.id())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người mua mới được phép thanh toán");
            }

            if (idempotencyKey == null || !idempotencyKey.matches("^[A-Za-z0-9_-]{8,64}$")) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key không hợp lệ");
            }

            // Key already exists for this order -> return old result
            Optional<Payment> existingOpt = paymentRepository.findByOrderIdAndIdempotencyKey(orderId, idempotencyKey);
            if (existingOpt.isPresent()) {
                Payment existing = existingOpt.get();
                return PaymentResponse.builder()
                        .paymentId(existing.getId())
                        .attemptNo(existing.getAttemptNo())
                        .status(existing.getStatus())
                        .orderStatus(order.getStatus())
                        .failureReason(existing.getFailureReason())
                        .build();
            }

            if (userRepository != null) {
                userRepository.findById(currentUser.id()).ifPresent(user -> {
                    if (user.getStatus() == UserStatus.BANNED) {
                        throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Tài khoản của bạn đã bị khóa");
                    }
                });
            }

            if (order.getStatus() == OrderStatus.CANCELLED) {
                throw new BusinessException(ErrorCode.ORDER_CANCELLED, "Đơn hàng đã bị hủy");
            }

            if (order.getStatus() == OrderStatus.EXPIRED) {
                throw new BusinessException(ErrorCode.ORDER_EXPIRED, "Đơn hàng đã hết hạn");
            }

            if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                        "Đơn hàng không ở trạng thái chờ thanh toán");
            }

            Instant now = Instant.now(clock);
            if (order.getPaymentDeadline() != null && !now.isBefore(order.getPaymentDeadline())) {
                throw new BusinessException(ErrorCode.ORDER_EXPIRED, "Đơn hàng đã quá hạn thanh toán");
            }

            if (order.getShippingAddress() == null || order.getShippingAddress().isBlank()
                    || order.getPhoneNumber() == null || order.getPhoneNumber().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Đơn hàng chưa có địa chỉ giao hàng hoặc số điện thoại");
            }

            int attemptNo = paymentRepository.findMaxAttemptNo(order.getId()).orElse(0) + 1;
            BigDecimal amount = order.getFinalPrice();

            PaymentStatus hint = null;
            if (paymentProperties != null && paymentProperties.isSimulateEnabled()) {
                hint = request.getSimulateResult();
            }

            GatewayRequest gwRequest = new GatewayRequest(order.getId(), amount, request.getMethod(), hint);
            GatewayResult gwResult = gateway.charge(gwRequest);

            Payment payment = Payment.builder()
                    .orderId(order.getId())
                    .attemptNo(attemptNo)
                    .amount(amount)
                    .method(request.getMethod())
                    .status(gwResult.outcome())
                    .idempotencyKey(idempotencyKey)
                    .failureReason(gwResult.failureReason())
                    .build();

            Payment saved = paymentRepository.save(payment);

            if (saved.getStatus() == PaymentStatus.SUCCESS) {
                order.markPaid(now);
                orderRepository.save(order);
                // TODO v4-phase-7: Notify buyer and seller after commit
            }

            return PaymentResponse.builder()
                    .paymentId(saved.getId())
                    .attemptNo(saved.getAttemptNo())
                    .status(saved.getStatus())
                    .orderStatus(order.getStatus())
                    .failureReason(saved.getFailureReason())
                    .build();
        });
    }

    @Override
    public List<PaymentHistoryResponse> getPaymentsByOrder(CurrentUser currentUser, Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng"));

        boolean isBuyer = order.getBuyerId().equals(currentUser.id());
        if (!isBuyer) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem lịch sử thanh toán của đơn này");
        }

        List<Payment> payments = paymentRepository.findByOrderIdOrderByAttemptNoDesc(orderId);
        return payments.stream()
                .map(this::toPaymentHistoryResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<PaymentHistoryResponse> getPaymentsByOrder(CurrentUser currentUser, Long orderId, Pageable pageable) {
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng")
        );

        boolean isBuyer = order.getBuyerId().equals(currentUser.id());
        if (!isBuyer) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem lịch sử thanh toán của đơn này");
        }

        Page<Payment> page = paymentRepository.findByOrderId(orderId, pageable);
        return page.map(this::toPaymentHistoryResponse);
    }

    private PaymentHistoryResponse toPaymentHistoryResponse(Payment p) {
        return PaymentHistoryResponse.builder()
                .paymentId(p.getId())
                .attemptNo(p.getAttemptNo())
                .method(p.getMethod())
                .amount(p.getAmount())
                .status(p.getStatus())
                .failureReason(p.getFailureReason())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
