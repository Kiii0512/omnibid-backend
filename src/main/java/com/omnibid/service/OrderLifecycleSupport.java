package com.omnibid.service;

import com.omnibid.common.NotificationTemplates;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.PaymentStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.ReportStatus;
import com.omnibid.enums.ReportType;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.PaymentRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.ReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
public class OrderLifecycleSupport {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private ReportRepository reportRepository;

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
    public void expireOne(Long orderId, LocalDateTime now) {
        Order preCheck = orderRepository.findById(orderId).orElse(null);
        if (preCheck == null) {
            return;
        }

        // Quy tắc khóa: Product -> Order (AUCTION_ORDER_INTEGRATION §4)
        Product product = productRepository.findByIdForUpdate(preCheck.getProductId()).orElse(null);
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);

        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }

        // Kiểm tra quá hạn
        if (now.isBefore(order.getPaymentDeadline())) {
            return;
        }

        // Không hết hạn nếu còn Payment PENDING (PAY-10) hoặc đã có SUCCESS
        if (paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING)
                || paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS)) {
            log.info("Order {} has PENDING or SUCCESS payment, skipping expiration", order.getId());
            return;
        }

        order.expire();
        orderRepository.save(order);

        // Hiệu ứng Product theo ORD-11: chỉ về AVAILABLE khi không HIDDEN/REMOVED
        if (product != null && product.getStatus() != ProductStatus.HIDDEN && product.getStatus() != ProductStatus.REMOVED) {
            product.setStatus(ProductStatus.AVAILABLE);
            productRepository.save(product);
        }

        // Thông báo ORDER_UPDATE cho buyer và seller
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
    public void sendReminder24h(Long orderId, LocalDateTime now) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }

        if (order.getReminder24hSentAt() != null) {
            return;
        }

        // Còn trong khoảng <= 24h và chưa quá hạn
        if (!now.isBefore(order.getPaymentDeadline()) || order.getPaymentDeadline().isAfter(now.plusHours(24))) {
            return;
        }

        order.setReminder24hSentAt(now);
        orderRepository.save(order);

        notificationService.notify(
                order.getBuyerId(),
                NotificationType.PAYMENT_REMINDER,
                NotificationTemplates.paymentReminderTitle("24 giờ", order.getId()),
                NotificationTemplates.paymentReminderContent(order.getId(), order.getPaymentDeadline()),
                "ORDER",
                order.getId()
        );

        log.info("Sent 24h payment reminder for order {}", order.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendReminder2h(Long orderId, LocalDateTime now) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }

        if (order.getReminder2hSentAt() != null) {
            return;
        }

        // Còn trong khoảng <= 2h và chưa quá hạn
        if (!now.isBefore(order.getPaymentDeadline()) || order.getPaymentDeadline().isAfter(now.plusHours(2))) {
            return;
        }

        order.setReminder2hSentAt(now);
        orderRepository.save(order);

        notificationService.notify(
                order.getBuyerId(),
                NotificationType.PAYMENT_REMINDER,
                NotificationTemplates.paymentReminderTitle("2 giờ", order.getId()),
                NotificationTemplates.paymentReminderContent(order.getId(), order.getPaymentDeadline()),
                "ORDER",
                order.getId()
        );

        log.info("Sent 2h payment reminder for order {}", order.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoCompleteOne(Long orderId, LocalDateTime now) {
        Order preCheck = orderRepository.findById(orderId).orElse(null);
        if (preCheck == null) {
            return;
        }

        // Khóa Product -> Order
        Product product = productRepository.findByIdForUpdate(preCheck.getProductId()).orElse(null);
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);

        if (order == null || order.getStatus() != OrderStatus.SHIPPING) {
            return;
        }

        if (order.getConfirmDeadline() == null || now.isBefore(order.getConfirmDeadline())) {
            return;
        }

        // Kiểm tra khiếu nại PENDING (ORD-09, UT-ORD-15)
        if (reportRepository != null && reportRepository.existsByOrderIdAndReportTypeAndStatus(order.getId(), ReportType.COMPLAINT, ReportStatus.PENDING)) {
            log.info("Order {} has PENDING complaint, skipping auto-completion", order.getId());
            return;
        }

        order.autoComplete(now);
        orderRepository.save(order);

        if (product != null) {
            product.setStatus(ProductStatus.SOLD);
            productRepository.save(product);
        }

        // Thông báo ORDER_UPDATE cho buyer & seller
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
