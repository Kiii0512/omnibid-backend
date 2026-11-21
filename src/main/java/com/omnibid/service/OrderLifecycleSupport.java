package com.omnibid.service;

import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.PaymentStatus;
import com.omnibid.enums.ReportStatus;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.PaymentRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.ReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
public class OrderLifecycleSupport {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private ReportRepository reportRepository;
    private com.omnibid.common.AfterCommitExecutor afterCommitExecutor;

    @Autowired
    public void setAfterCommitExecutor(com.omnibid.common.AfterCommitExecutor afterCommitExecutor) {
        this.afterCommitExecutor = afterCommitExecutor;
    }

    public OrderLifecycleSupport(OrderRepository orderRepository,
                                 ProductRepository productRepository,
                                 PaymentRepository paymentRepository,
                                 NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
    }

    @Autowired
    public OrderLifecycleSupport(OrderRepository orderRepository,
                                 ProductRepository productRepository,
                                 PaymentRepository paymentRepository,
                                 NotificationService notificationService,
                                 ReportRepository reportRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
        this.reportRepository = reportRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOne(Long orderId, Instant now) {
        Order initial = orderRepository.findById(orderId).orElse(null);
        if (initial == null || initial.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }

        if (now.isBefore(initial.getPaymentDeadline())) {
            return;
        }

        // Lock order
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }

        Product product = order.getProductId() != null
                ? productRepository.findByIdForUpdate(order.getProductId()).orElse(null)
                : null;

        if (now.isBefore(order.getPaymentDeadline())) {
            return;
        }

        if (paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS)) {
            log.info("Order {} has SUCCESS payment, skipping expiration", order.getId());
            return;
        }

        order.expire(now);
        orderRepository.save(order);

        if (product != null) {
            product.release();
            productRepository.save(product);
        }

        // TODO v4-phase-7: Notify buyer and seller after commit
        Long buyerId = order.getBuyerId();
        Long sellerId = order.getSellerId();
        Long oId = order.getId();
        if (afterCommitExecutor != null && notificationService != null) {
            afterCommitExecutor.execute(() -> {
                notificationService.notify(
                        buyerId,
                        com.omnibid.enums.NotificationType.ORDER_UPDATE,
                        com.omnibid.common.NotificationTemplates.orderExpiredTitle(oId),
                        com.omnibid.common.NotificationTemplates.orderExpiredContent(oId),
                        "ORDER",
                        oId
                );
                notificationService.notify(
                        sellerId,
                        com.omnibid.enums.NotificationType.ORDER_UPDATE,
                        com.omnibid.common.NotificationTemplates.orderExpiredTitle(oId),
                        com.omnibid.common.NotificationTemplates.orderExpiredContent(oId),
                        "ORDER",
                        oId
                );
            });
        }
        log.info("Order {} EXPIRED at {}", order.getId(), now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoCompleteOne(Long orderId, Instant now) {
        Order initial = orderRepository.findById(orderId).orElse(null);
        if (initial == null || initial.getStatus() != OrderStatus.SHIPPING) {
            return;
        }

        if (initial.getConfirmDeadline() == null || now.isBefore(initial.getConfirmDeadline())) {
            return;
        }

        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.SHIPPING) {
            return;
        }

        Product product = order.getProductId() != null
                ? productRepository.findByIdForUpdate(order.getProductId()).orElse(null)
                : null;

        if (order.getConfirmDeadline() == null || now.isBefore(order.getConfirmDeadline())) {
            return;
        }

        order.autoComplete(now);
        orderRepository.save(order);

        if (product != null) {
            product.markSold();
            productRepository.save(product);
        }

        // TODO v4-phase-7: Notify buyer and seller after commit
        Long buyerId = order.getBuyerId();
        Long sellerId = order.getSellerId();
        Long oId = order.getId();
        if (afterCommitExecutor != null && notificationService != null) {
            afterCommitExecutor.execute(() -> {
                notificationService.notify(
                        buyerId,
                        com.omnibid.enums.NotificationType.ORDER_UPDATE,
                        com.omnibid.common.NotificationTemplates.orderAutoCompleteTitle(oId),
                        com.omnibid.common.NotificationTemplates.orderAutoCompleteContent(oId),
                        "ORDER",
                        oId
                );
                notificationService.notify(
                        sellerId,
                        com.omnibid.enums.NotificationType.ORDER_UPDATE,
                        com.omnibid.common.NotificationTemplates.orderAutoCompleteTitle(oId),
                        com.omnibid.common.NotificationTemplates.orderAutoCompleteContent(oId),
                        "ORDER",
                        oId
                );
            });
        }
        log.info("Order {} auto-COMPLETED at {}", order.getId(), now);
    }
}
