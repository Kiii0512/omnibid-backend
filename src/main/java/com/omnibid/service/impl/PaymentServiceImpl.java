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
import com.omnibid.security.CurrentUser;
import com.omnibid.service.NotificationService;
import com.omnibid.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final String UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final MockPaymentGateway gateway;
    private final NotificationService notificationService;
    private final TransactionOperations transactionOperations;
    private final AppProperties.Payment paymentProperties;
    private final Clock clock;

    @Autowired
    public PaymentServiceImpl(OrderRepository orderRepository,
                              PaymentRepository paymentRepository,
                              MockPaymentGateway gateway,
                              NotificationService notificationService,
                              PlatformTransactionManager transactionManager,
                              AppProperties appProperties,
                              Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.notificationService = notificationService;
        this.transactionOperations = new TransactionTemplate(transactionManager);
        this.paymentProperties = appProperties.getPayment();
        this.clock = clock;
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
    }

    @Override
    public PaymentResponse pay(CurrentUser currentUser, Long orderId, String idempotencyKey, PaymentRequest request) {
        if (currentUser.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người mua mới được phép thanh toán");
        }
        if (idempotencyKey == null || !idempotencyKey.trim().matches(UUID_REGEX)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key không hợp lệ (yêu cầu định dạng UUID)");
        }
        String cleanKey = idempotencyKey.trim();

        return transactionOperations.execute(status -> {
            Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(
                    () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng"));

            if (!order.getBuyerId().equals(currentUser.id())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người mua mới được phép thanh toán");
            }

            Optional<Payment> existingOpt = paymentRepository.findByOrderIdAndIdempotencyKey(orderId, cleanKey);
            if (existingOpt.isPresent()) {
                Payment existing = existingOpt.get();
                return toPaymentResponse(existing, order.getStatus());
            }

            if (order.getStatus() == OrderStatus.CANCELLED) {
                throw new BusinessException(ErrorCode.ORDER_CANCELLED, "Đơn hàng đã bị hủy");
            }

            Instant now = Instant.now(clock);
            if (order.getStatus() == OrderStatus.EXPIRED || (order.getPaymentDeadline() != null && !now.isBefore(order.getPaymentDeadline()))) {
                throw new BusinessException(ErrorCode.ORDER_EXPIRED, "Đơn hàng đã quá hạn thanh toán");
            }

            if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                        "Đơn hàng không ở trạng thái chờ thanh toán");
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

            GatewayRequest gwRequest = new GatewayRequest(order.getId(), amount, PaymentMethod.MOCK_CARD, hint);
            GatewayResult gwResult = gateway.charge(gwRequest);

            Payment payment = Payment.builder()
                    .orderId(order.getId())
                    .attemptNo(attemptNo)
                    .amount(amount)
                    .method(PaymentMethod.MOCK_CARD)
                    .status(gwResult.outcome())
                    .idempotencyKey(cleanKey)
                    .failureReason(gwResult.failureReason())
                    .paidAt(gwResult.outcome() == PaymentStatus.SUCCESS ? now : null)
                    .build();

            Payment saved;
            try {
                saved = paymentRepository.saveAndFlush(payment);
            } catch (DataIntegrityViolationException ex) {
                Optional<Payment> conflictExisting = paymentRepository.findByOrderIdAndIdempotencyKey(orderId, cleanKey);
                if (conflictExisting.isPresent()) {
                    return toPaymentResponse(conflictExisting.get(), order.getStatus());
                }
                throw new BusinessException(ErrorCode.CONFLICT, "Xung đột dữ liệu thanh toán");
            }

            if (saved.getStatus() == PaymentStatus.SUCCESS) {
                order.markPaid(now);
                orderRepository.save(order);
                // TODO v4-phase-7: Notify buyer and seller after commit
            }

            return toPaymentResponse(saved, order.getStatus());
        });
    }

    @Override
    public List<PaymentHistoryResponse> getPaymentsByOrder(CurrentUser currentUser, Long orderId) {
        if (currentUser.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Admin không có quyền truy cập lịch sử thanh toán qua API này");
        }
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng"));

        if (!order.getBuyerId().equals(currentUser.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem lịch sử thanh toán của đơn này");
        }

        List<Payment> payments = paymentRepository.findByOrderIdOrderByAttemptNoDesc(orderId);
        return payments.stream().map(this::toPaymentHistoryResponse).collect(Collectors.toList());
    }

    @Override
    public Page<PaymentHistoryResponse> getPaymentsByOrder(CurrentUser currentUser, Long orderId, Pageable pageable) {
        if (currentUser.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Admin không có quyền truy cập lịch sử thanh toán qua API này");
        }
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng"));

        if (!order.getBuyerId().equals(currentUser.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem lịch sử thanh toán của đơn này");
        }

        Page<Payment> page = paymentRepository.findByOrderId(orderId, pageable);
        return page.map(this::toPaymentHistoryResponse);
    }

    private PaymentResponse toPaymentResponse(Payment p, OrderStatus orderStatus) {
        return PaymentResponse.builder()
                .paymentId(p.getId())
                .attemptNo(p.getAttemptNo())
                .status(p.getStatus())
                .orderStatus(orderStatus)
                .failureReason(p.getFailureReason())
                .build();
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
