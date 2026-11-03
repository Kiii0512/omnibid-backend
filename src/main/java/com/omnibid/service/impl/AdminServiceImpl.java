package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.entity.Payment;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.*;
import com.omnibid.repository.*;
import com.omnibid.service.AdminService;
import com.omnibid.service.AuditService;
import com.omnibid.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final Clock clock;

    @Override
    @Transactional
    public Map<String, Object> completeOrder(Long orderId, String note, Long adminId) {
        Order orderToInspect = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND));

        // Lock hierarchy: Product (L3) -> Order (L4)
        Product product = productRepository.findByIdForUpdate(orderToInspect.getProductId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "Product not found", HttpStatus.NOT_FOUND));

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND));

        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.SHIPPING) {
            throw new AppException("INVALID_STATUS_TRANSITION",
                    "Cannot complete order in status: " + order.getStatus(), HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        order.complete(now);
        orderRepository.save(order);

        product.setStatus(ProductStatus.SOLD);
        productRepository.save(product);

        auditService.log(adminId, "ORDER_COMPLETE", "ORDER", order.getId(), note);

        notificationService.notify(order.getBuyerId(), NotificationType.ORDER_UPDATE,
                NotificationTemplates.orderCompletedTitle(order.getId()),
                NotificationTemplates.orderCompletedContent(order.getId()),
                "ORDER", order.getId());

        notificationService.notify(order.getSellerId(), NotificationType.ORDER_UPDATE,
                NotificationTemplates.orderCompletedTitle(order.getId()),
                NotificationTemplates.orderCompletedContent(order.getId()),
                "ORDER", order.getId());

        return Map.of("orderStatus", "COMPLETED", "productStatus", "SOLD");
    }

    @Override
    @Transactional
    public Map<String, Object> cancelOrder(Long orderId, String reason, Long adminId) {
        Order orderToInspect = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND));

        // Lock hierarchy: Product (L3) -> Order (L4) -> Payment (L5)
        Product product = productRepository.findByIdForUpdate(orderToInspect.getProductId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "Product not found", HttpStatus.NOT_FOUND));

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND));

        if (order.getStatus() == OrderStatus.COMPLETED
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.EXPIRED) {
            throw new AppException("INVALID_STATUS_TRANSITION",
                    "Cannot cancel order in status: " + order.getStatus(), HttpStatus.CONFLICT);
        }

        // Cancel pending payments (L5)
        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        for (Payment payment : payments) {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("ORDER_CANCELLED");
                paymentRepository.save(payment);
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        order.cancel(reason, now);
        orderRepository.save(order);

        if (product.getStatus() != ProductStatus.HIDDEN && product.getStatus() != ProductStatus.REMOVED) {
            product.setStatus(ProductStatus.AVAILABLE);
            productRepository.save(product);
        }

        auditService.log(adminId, "ORDER_CANCEL", "ORDER", order.getId(), reason);

        notificationService.notify(order.getBuyerId(), NotificationType.ORDER_UPDATE,
                NotificationTemplates.orderCancelledTitle(order.getId(), reason),
                NotificationTemplates.orderCancelledContent(order.getId(), reason),
                "ORDER", order.getId());

        notificationService.notify(order.getSellerId(), NotificationType.ORDER_UPDATE,
                NotificationTemplates.orderCancelledTitle(order.getId(), reason),
                NotificationTemplates.orderCancelledContent(order.getId(), reason),
                "ORDER", order.getId());

        return Map.of("orderStatus", "CANCELLED", "productStatus", product.getStatus().name());
    }

    @Override
    @Transactional
    public Map<String, Object> hideProduct(Long productId, String reason, Long adminId) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Product not found", HttpStatus.NOT_FOUND));

        if (product.getStatus() == ProductStatus.HIDDEN
                || product.getStatus() == ProductStatus.REMOVED
                || product.getStatus() == ProductStatus.SOLD) {
            throw new AppException("INVALID_STATUS_TRANSITION",
                    "Cannot hide product in status: " + product.getStatus(), HttpStatus.CONFLICT);
        }

        product.setStatus(ProductStatus.HIDDEN);
        productRepository.save(product);

        auditService.log(adminId, "PRODUCT_HIDE", "PRODUCT", productId, reason);

        return Map.of("productId", productId, "productStatus", "HIDDEN");
    }

    @Override
    @Transactional
    public Map<String, Object> cancelAuction(Long auctionId, String reason, Long adminId) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Auction not found", HttpStatus.NOT_FOUND));

        if (auction.getStatus() != AuctionStatus.SCHEDULED && auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new AppException("INVALID_STATUS_TRANSITION",
                    "Cannot cancel auction in status: " + auction.getStatus(), HttpStatus.CONFLICT);
        }

        auction.setStatus(AuctionStatus.CANCELLED);
        auction.setCancelReason(reason);
        auction.setClosedAt(LocalDateTime.now(clock));
        auctionRepository.save(auction);

        auditService.log(adminId, "AUCTION_CANCEL", "AUCTION", auctionId, reason);

        return Map.of("auctionId", auctionId, "auctionStatus", "CANCELLED");
    }

    @Override
    @Transactional
    public Map<String, Object> banUser(Long userId, String reason, Long adminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));

        user.setStatus(UserStatus.BANNED);
        userRepository.save(user);

        auditService.log(adminId, "USER_BAN", "USER", userId, reason);

        return Map.of("userId", userId, "status", "BANNED");
    }
}
