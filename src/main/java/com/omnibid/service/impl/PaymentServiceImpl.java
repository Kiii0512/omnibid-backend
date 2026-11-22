package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.payment.PaymentHistoryResponse;
import com.omnibid.dto.payment.PaymentRequest;
import com.omnibid.dto.payment.PaymentResponse;
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
import com.omnibid.service.NotificationService;
import com.omnibid.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final MockPaymentGateway gateway;
    private final NotificationService notificationService;
    private final AppProperties appProperties;
    private final Clock clock;

    @Override
    @Transactional
    public PaymentResponse pay(CurrentUser currentUser, Long orderId, String idempotencyKey, PaymentRequest request) {
        // TODO v4-phase-6: refine synchronous payment validation and response fields per API_SPEC v4
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng")
        );

        if (!order.getBuyerId().equals(currentUser.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người mua mới được phép thanh toán");
        }

        if (idempotencyKey == null || !idempotencyKey.matches("^[A-Za-z0-9_-]{8,64}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key không hợp lệ");
        }

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

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ORDER_CANCELLED, "Đơn hàng đã bị hủy");
        }
        if (order.getStatus() == OrderStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.ORDER_EXPIRED, "Đơn hàng đã hết hạn");
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
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Đơn hàng chưa có địa chỉ giao hàng hoặc số điện thoại");
        }

        if (paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Đơn hàng đã có giao dịch thanh toán thành công");
        }

        PaymentStatus hint = appProperties.getPayment().isSimulateEnabled() ? request.getSimulateResult() : null;
        PaymentMethod method = request.getMethod() != null ? request.getMethod() : PaymentMethod.MOCK_CARD;
        GatewayResult gwResult = gateway.charge(new GatewayRequest(order.getFinalPrice(), method, hint));

        int attemptNo = paymentRepository.findMaxAttemptNo(order.getId()) + 1;
        boolean success = gwResult.status() == PaymentStatus.SUCCESS;

        Payment payment = Payment.builder()
                .orderId(order.getId())
                .attemptNo(attemptNo)
                .amount(order.getFinalPrice())
                .method(method)
                .status(gwResult.status())
                .idempotencyKey(idempotencyKey)
                .failureReason(success ? null : (gwResult.failureReason() != null ? gwResult.failureReason() : "MOCK_DECLINED"))
                .paidAt(success ? now : null)
                .build();

        try {
            payment = paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException dive) {
            log.warn("Data integrity violation when inserting payment for orderId={}: {}", order.getId(), dive.getMessage());
            throw new BusinessException(ErrorCode.CONFLICT, "Xung đột giao dịch thanh toán");
        }

        if (success) {
            order.markPaid(now);
            orderRepository.save(order);

            notificationService.notify(order.getBuyerId(), NotificationType.ORDER_UPDATE,
                    NotificationTemplates.paymentSuccessBuyerTitle(),
                    NotificationTemplates.paymentSuccessBuyerContent(order.getId()),
                    "ORDER", order.getId());

            notificationService.notify(order.getSellerId(), NotificationType.ORDER_UPDATE,
                    NotificationTemplates.paymentSuccessSellerTitle(order.getId()),
                    NotificationTemplates.paymentSuccessSellerContent(order.getId()),
                    "ORDER", order.getId());
        }

        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .attemptNo(payment.getAttemptNo())
                .status(payment.getStatus())
                .orderStatus(order.getStatus())
                .failureReason(payment.getFailureReason())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> getPaymentsByOrder(CurrentUser currentUser, Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng")
        );

        boolean isBuyer = order.getBuyerId().equals(currentUser.id());
        if (!isBuyer) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem lịch sử thanh toán của đơn này");
        }

        List<Payment> payments = paymentRepository.findByOrderIdOrderByAttemptNoDesc(orderId);
        return payments.stream()
                .map(p -> PaymentHistoryResponse.builder()
                        .paymentId(p.getId())
                        .attemptNo(p.getAttemptNo())
                        .method(p.getMethod())
                        .amount(p.getAmount())
                        .status(p.getStatus())
                        .failureReason(p.getFailureReason())
                        .paidAt(p.getPaidAt())
                        .createdAt(p.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
