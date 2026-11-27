package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.common.PageResponse;
import com.omnibid.common.SortWhitelist;
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
import com.omnibid.service.NotificationService;
import com.omnibid.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private static final Set<String> ALLOWED_PAYMENT_SORTS = Set.of(
            "attemptNo", "createdAt", "paidAt", "amount", "status"
    );

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderAccessPolicy orderAccessPolicy;
    private final MockPaymentGateway gateway;
    private final NotificationService notificationService;
    private final Clock clock;
    private final TransactionTemplate writeTxTemplate;
    private final TransactionTemplate recoveryTxTemplate;

    @Autowired
    public PaymentServiceImpl(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            OrderAccessPolicy orderAccessPolicy,
            MockPaymentGateway gateway,
            NotificationService notificationService,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.orderAccessPolicy = orderAccessPolicy;
        this.gateway = gateway;
        this.notificationService = notificationService;
        this.clock = clock;

        this.writeTxTemplate = new TransactionTemplate(transactionManager);

        TransactionTemplate readRecovery = new TransactionTemplate(transactionManager);
        readRecovery.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        readRecovery.setReadOnly(true);
        this.recoveryTxTemplate = readRecovery;
    }

    public PaymentServiceImpl(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            OrderAccessPolicy orderAccessPolicy,
            MockPaymentGateway gateway,
            NotificationService notificationService,
            Clock clock,
            TransactionTemplate transactionTemplate
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.orderAccessPolicy = orderAccessPolicy;
        this.gateway = gateway;
        this.notificationService = notificationService;
        this.clock = clock;
        this.writeTxTemplate = transactionTemplate;
        this.recoveryTxTemplate = transactionTemplate;
    }

    @Override
    public PaymentResultResponse pay(CurrentUser currentUser, Long orderId, String idempotencyKey, PaymentRequest request) {
        String normalizedKey = validateIdempotencyKey(idempotencyKey);

        try {
            return writeTxTemplate.execute(status -> doPayInTransaction(currentUser, orderId, normalizedKey, request));
        } catch (DataIntegrityViolationException dive) {
            // The write transaction has rolled back cleanly; perform idempotency recheck in a fresh read-only transaction
            // so we never touch a corrupted Hibernate session or trigger UnexpectedRollbackException.
            log.warn("Data integrity violation when inserting payment for orderId={}: {}", orderId, dive.getMessage());
            Optional<PaymentResultResponse> recovered = recoveryTxTemplate.execute(status ->
                    paymentRepository.findByOrderIdAndIdempotencyKey(orderId, normalizedKey)
                            .map(existing -> {
                                OrderStatus latestStatus = orderRepository.findById(orderId)
                                        .map(Order::getStatus)
                                        .orElse(OrderStatus.PENDING_PAYMENT);
                                return toResult(existing, latestStatus);
                            })
            );
            if (recovered != null && recovered.isPresent()) {
                return recovered.get();
            }
            throw new BusinessException(ErrorCode.CONFLICT, "Xung đột giao dịch thanh toán");
        }
    }

    private PaymentResultResponse doPayInTransaction(
            CurrentUser currentUser,
            Long orderId,
            String normalizedKey,
            PaymentRequest request
    ) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng")
        );

        orderAccessPolicy.requireBuyer(order, currentUser);

        Optional<Payment> existingOpt = paymentRepository.findByOrderIdAndIdempotencyKey(orderId, normalizedKey);
        if (existingOpt.isPresent()) {
            return toResult(existingOpt.get(), order.getStatus());
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ORDER_CANCELLED, "Đơn hàng đã bị hủy");
        }
        if (order.getStatus() == OrderStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.ORDER_EXPIRED, "Đơn hàng đã hết hạn thanh toán");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Đơn hàng không ở trạng thái chờ thanh toán");
        }

        Instant now = Instant.now(clock);
        if (order.isPaymentOverdue(now)) {
            throw new BusinessException(ErrorCode.ORDER_EXPIRED, "Đơn hàng đã quá hạn thanh toán");
        }

        if (order.getShippingAddress() == null || order.getShippingAddress().isBlank()
                || order.getPhoneNumber() == null || order.getPhoneNumber().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Đơn hàng chưa có thông tin giao hàng");
        }

        if (paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Đơn hàng đã có giao dịch thanh toán thành công");
        }

        int attemptNo = paymentRepository.findMaxAttemptNo(order.getId()) + 1;
        PaymentMethod method = request != null && request.getMethod() != null
                ? request.getMethod()
                : PaymentMethod.MOCK_CARD;
        PaymentStatus simulateResult = request != null ? request.getSimulateResult() : null;

        // Note: synchronous mock gateway runs in-process. When migrating to a real external HTTP payment gateway,
        // move gateway.charge() outside the DB transaction using an async/webhook state machine to avoid holding locks over network I/O.
        GatewayResult gwResult = gateway.charge(new GatewayRequest(order.getFinalPrice(), method, simulateResult));
        boolean success = gwResult.status() == PaymentStatus.SUCCESS;

        Payment payment = Payment.builder()
                .orderId(order.getId())
                .attemptNo(attemptNo)
                .amount(order.getFinalPrice())
                .method(method)
                .status(gwResult.status())
                .idempotencyKey(normalizedKey)
                .failureReason(success ? null : (gwResult.failureReason() != null ? gwResult.failureReason() : "MOCK_DECLINED"))
                .paidAt(success ? now : null)
                .build();

        payment = paymentRepository.saveAndFlush(payment);

        if (success) {
            order.markPaid(now);
            orderRepository.save(order);

            notificationService.notify(
                    order.getBuyerId(),
                    NotificationType.ORDER_UPDATE,
                    NotificationTemplates.paymentSuccessBuyerTitle(),
                    NotificationTemplates.paymentSuccessBuyerContent(order.getId()),
                    "ORDER",
                    order.getId()
            );

            notificationService.notify(
                    order.getSellerId(),
                    NotificationType.ORDER_UPDATE,
                    NotificationTemplates.paymentSuccessSellerTitle(order.getId()),
                    NotificationTemplates.paymentSuccessSellerContent(order.getId()),
                    "ORDER",
                    order.getId()
            );
        }

        return toResult(payment, order.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentHistoryResponse> getPaymentsByOrder(CurrentUser currentUser, Long orderId, Pageable pageable) {
        SortWhitelist.validate(pageable, ALLOWED_PAYMENT_SORTS);

        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng")
        );

        orderAccessPolicy.requireBuyer(order, currentUser);

        Page<Payment> page = paymentRepository.findByOrderId(orderId, pageable);
        return PageResponse.from(page, this::toHistoryResponse);
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thiếu header bắt buộc: Idempotency-Key",
                    Map.of("fields", Map.of("Idempotency-Key", "Header bắt buộc"))
            );
        }
        String trimmed = idempotencyKey.trim();
        if (!UUID_PATTERN.matcher(trimmed).matches()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key không đúng định dạng UUID",
                    Map.of("fields", Map.of("Idempotency-Key", "Phải là định dạng UUID hợp lệ"))
            );
        }
        return trimmed;
    }

    private PaymentResultResponse toResult(Payment payment, OrderStatus orderStatus) {
        return PaymentResultResponse.builder()
                .paymentId(payment.getId())
                .attemptNo(payment.getAttemptNo())
                .status(payment.getStatus())
                .orderStatus(orderStatus)
                .failureReason(payment.getStatus() == PaymentStatus.FAILED ? payment.getFailureReason() : null)
                .build();
    }

    private PaymentHistoryResponse toHistoryResponse(Payment payment) {
        return PaymentHistoryResponse.builder()
                .id(payment.getId())
                .attemptNo(payment.getAttemptNo())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .paidAt(payment.getPaidAt())
                .build();
    }
}
