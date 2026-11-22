package com.omnibid.service;

import com.omnibid.common.NotificationTemplates;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.PaymentStatus;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.PaymentRepository;
import com.omnibid.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderLifecycleSupport {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOne(Long orderId, Instant now) {
        // Lock order -> product per v4 lock ordering (auctions -> orders -> products)
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }
        if (!order.isPaymentOverdue(now)) {
            return;
        }
        if (paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS)) {
            log.info("Order {} already has SUCCESS payment, skipping expiration", order.getId());
            return;
        }

        Product product = order.getProductId() != null
                ? productRepository.findByIdForUpdate(order.getProductId()).orElse(null)
                : null;

        order.expire(now);
        orderRepository.save(order);

        if (product != null) {
            product.release();
            productRepository.save(product);
        }

        notificationService.notify(
                order.getBuyerId(),
                NotificationType.ORDER_UPDATE,
                NotificationTemplates.orderExpiredTitle(order.getId()),
                NotificationTemplates.orderExpiredContent(order.getId()),
                "ORDER",
                order.getId()
        );

        notificationService.notify(
                order.getSellerId(),
                NotificationType.ORDER_UPDATE,
                NotificationTemplates.orderExpiredTitle(order.getId()),
                NotificationTemplates.orderExpiredContent(order.getId()),
                "ORDER",
                order.getId()
        );

        log.info("Order {} EXPIRED at {}", order.getId(), now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoCompleteOne(Long orderId, Instant now) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.SHIPPING) {
            return;
        }
        if (order.getConfirmDeadline() == null || now.isBefore(order.getConfirmDeadline())) {
            return;
        }

        Product product = order.getProductId() != null
                ? productRepository.findByIdForUpdate(order.getProductId()).orElse(null)
                : null;

        order.autoComplete(now);
        orderRepository.save(order);

        if (product != null) {
            product.markSold();
            productRepository.save(product);
        }

        notificationService.notify(
                order.getBuyerId(),
                NotificationType.ORDER_UPDATE,
                NotificationTemplates.orderCompletedTitle(order.getId()),
                NotificationTemplates.orderCompletedContent(order.getId()),
                "ORDER",
                order.getId()
        );

        notificationService.notify(
                order.getSellerId(),
                NotificationType.ORDER_UPDATE,
                NotificationTemplates.orderCompletedTitle(order.getId()),
                NotificationTemplates.orderCompletedContent(order.getId()),
                "ORDER",
                order.getId()
        );

        log.info("Order {} auto-COMPLETED at {}", order.getId(), now);
    }
}
