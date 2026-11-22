package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.MaskingUtils;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.common.PageResponse;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.order.OrderConfirmReceiptResponse;
import com.omnibid.dto.order.OrderDetailResponse;
import com.omnibid.dto.order.OrderResponse;
import com.omnibid.dto.order.OrderUserSummaryResponse;
import com.omnibid.dto.order.ShippingInfoRequest;
import com.omnibid.entity.*;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.*;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.OrderAccessPolicy;
import com.omnibid.service.NotificationService;
import com.omnibid.service.OrderLifecycleSupport;
import com.omnibid.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final OrderAccessPolicy orderAccessPolicy;
    private final OrderLifecycleSupport orderLifecycleSupport;
    private final AppProperties appProperties;
    private final Clock clock;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public Order createFromAuction(Auction auction) {
        return orderRepository.findByAuctionId(auction.getId())
                .orElseGet(() -> {
                    Duration deadline = appProperties.getOrder().getPaymentDeadlineHours();
                    Instant closedAt = auction.getClosedAt() != null ? auction.getClosedAt() : Instant.now(clock);
                    Order order = Order.builder()
                            .auctionId(auction.getId())
                            .productId(auction.getProductId())
                            .sellerId(auction.getSellerId())
                            .buyerId(auction.getWinnerId())
                            .finalPrice(auction.getFinalPrice())
                            .status(OrderStatus.PENDING_PAYMENT)
                            .paymentDeadline(closedAt.plus(deadline))
                            .build();
                    log.info("Creating order for auction {}: buyer={}, amount={}",
                            auction.getId(), auction.getWinnerId(), auction.getFinalPrice());
                    return orderRepository.save(order);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getOrders(CurrentUser currentUser, String role, OrderStatus status, Pageable pageable) {
        Page<Order> page;
        if (currentUser.isAdmin()) {
            page = orderRepository.findAllByStatus(status, pageable);
        } else if ("buyer".equalsIgnoreCase(role)) {
            page = orderRepository.findByBuyerId(currentUser.id(), status, pageable);
        } else if ("seller".equalsIgnoreCase(role)) {
            page = orderRepository.findBySellerId(currentUser.id(), status, pageable);
        } else {
            page = orderRepository.findByBuyerOrSeller(currentUser.id(), status, pageable);
        }

        return PageResponse.from(page, order -> mapToOrderResponse(order, currentUser));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderById(CurrentUser currentUser, Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Order not found with id: " + id));

        orderAccessPolicy.requireViewer(order, currentUser);

        return mapToOrderDetailResponse(order, currentUser);
    }

    @Override
    @Transactional
    public OrderDetailResponse updateShippingInfo(CurrentUser currentUser, Long id, ShippingInfoRequest request) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Order not found with id: " + id));

        orderAccessPolicy.requireBuyer(order, currentUser);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ORDER_CANCELLED, "Order has been cancelled");
        }

        // TODO v4-phase-6: verify allowed statuses for updateShippingInfo per API_SPEC v4
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Cannot update shipping info when order is " + order.getStatus());
        }

        order.setShippingAddress(request.getShippingAddress());
        order.setPhoneNumber(request.getPhoneNumber());
        Order saved = orderRepository.save(order);

        return mapToOrderDetailResponse(saved, currentUser);
    }

    @Override
    public void expireOverdueOrders() {
        Instant now = Instant.now(clock);
        List<Long> overdueOrderIds = orderRepository.findIdsByStatusAndPaymentDeadlineLessThan(
                OrderStatus.PENDING_PAYMENT, now
        );

        for (Long orderId : overdueOrderIds) {
            try {
                orderLifecycleSupport.expireOne(orderId, now);
            } catch (Exception ex) {
                log.error("Failed to expire order {}", orderId, ex);
            }
        }
    }

    @Override
    @Transactional
    public OrderConfirmReceiptResponse confirmReceipt(CurrentUser currentUser, Long id) {
        Order order = orderRepository.findByIdForUpdate(id).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng")
        );

        if (!order.getBuyerId().equals(currentUser.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người mua mới được phép xác nhận nhận hàng");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ORDER_CANCELLED, "Đơn hàng đã bị hủy");
        }

        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Đơn hàng chưa ở trạng thái đang giao hàng");
        }

        Instant now = Instant.now(clock);
        order.confirmReceived(now);
        orderRepository.save(order);

        Product product = productRepository.findByIdForUpdate(order.getProductId())
                .or(() -> productRepository.findById(order.getProductId()))
                .orElse(null);
        if (product != null) {
            product.markSold();
            productRepository.save(product);
        }

        if (notificationService != null) {
            notificationService.notify(
                    order.getSellerId(),
                    NotificationType.ORDER_UPDATE,
                    NotificationTemplates.orderReceiptConfirmedTitle(),
                    NotificationTemplates.orderReceiptConfirmedContent(order.getId()),
                    "ORDER",
                    order.getId()
            );
        }

        return OrderConfirmReceiptResponse.builder()
                .id(order.getId())
                .orderStatus(order.getStatus())
                .productStatus(product != null ? product.getStatus() : ProductStatus.SOLD)
                .build();
    }

    @Override
    public void autoCompleteOrders() {
        Instant now = Instant.now(clock);
        List<Long> overdueOrderIds = orderRepository.findIdsByStatusAndConfirmDeadlineLessThan(OrderStatus.SHIPPING, now);
        for (Long orderId : overdueOrderIds) {
            try {
                orderLifecycleSupport.autoCompleteOne(orderId, now);
            } catch (Exception ex) {
                log.error("Failed to auto-complete order id={}", orderId, ex);
            }
        }
    }

    private OrderResponse mapToOrderResponse(Order order, CurrentUser currentUser) {
        String productTitle = null;
        String productImageUrl = null;

        Product product = productRepository.findById(order.getProductId()).orElse(null);
        if (product != null) {
            productTitle = product.getTitle();
        }

        ProductImage primaryImg = productImageRepository.findByProductIdAndIsPrimaryTrue(order.getProductId())
                .orElse(null);
        if (primaryImg != null) {
            productImageUrl = primaryImg.getImageUrl();
        }

        String viewerRole = determineViewerRole(order, currentUser);

        return OrderResponse.builder()
                .id(order.getId())
                .auctionId(order.getAuctionId())
                .productId(order.getProductId())
                .productTitle(productTitle)
                .productImageUrl(productImageUrl)
                .finalPrice(order.getFinalPrice())
                .status(order.getStatus())
                .viewerRole(viewerRole)
                .paymentDeadline(order.getPaymentDeadline())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderDetailResponse mapToOrderDetailResponse(Order order, CurrentUser currentUser) {
        String productTitle = null;
        String productImageUrl = null;
        String sellerName = null;
        String buyerName = null;

        Product product = productRepository.findById(order.getProductId()).orElse(null);
        if (product != null) {
            productTitle = product.getTitle();
        }

        ProductImage primaryImg = productImageRepository.findByProductIdAndIsPrimaryTrue(order.getProductId())
                .orElse(null);
        if (primaryImg != null) {
            productImageUrl = primaryImg.getImageUrl();
        }

        User seller = userRepository.findById(order.getSellerId()).orElse(null);
        if (seller != null) {
            sellerName = MaskingUtils.maskName(seller.getFullName());
        }
        User buyer = userRepository.findById(order.getBuyerId()).orElse(null);
        if (buyer != null) {
            buyerName = MaskingUtils.maskName(buyer.getFullName());
        }

        OrderDetailResponse.PaymentSummaryDto latestPaymentDto = null;
        List<Payment> payments = paymentRepository.findByOrderIdOrderByAttemptNoDesc(order.getId());
        if (!payments.isEmpty()) {
            Payment latestPayment = payments.get(0);
            latestPaymentDto = OrderDetailResponse.PaymentSummaryDto.builder()
                    .paymentId(latestPayment.getId())
                    .attemptNo(latestPayment.getAttemptNo())
                    .status(latestPayment.getStatus())
                    .failureReason(latestPayment.getFailureReason())
                    .build();
        }

        String viewerRole = determineViewerRole(order, currentUser);
        List<String> allowedActions = calculateAllowedActions(order, viewerRole);

        return OrderDetailResponse.builder()
                .id(order.getId())
                .auctionId(order.getAuctionId())
                .productId(order.getProductId())
                .productTitle(productTitle)
                .productImageUrl(productImageUrl)
                .sellerId(order.getSellerId())
                .sellerName(sellerName)
                .buyerId(order.getBuyerId())
                .buyerName(buyerName)
                .finalPrice(order.getFinalPrice())
                .status(order.getStatus())
                .shippingAddress(order.getShippingAddress())
                .phoneNumber(order.getPhoneNumber())
                .paymentDeadline(order.getPaymentDeadline())
                .paidAt(order.getPaidAt())
                .trackingCode(order.getTrackingCode())
                .shippedAt(order.getShippedAt())
                .confirmDeadline(order.getConfirmDeadline())
                .completedAt(order.getCompletedAt())
                .cancelledAt(order.getCancelledAt())
                .cancelReason(order.getCancelReason())
                .latestPayment(latestPaymentDto)
                .viewerRole(viewerRole)
                .allowedActions(allowedActions)
                .build();
    }

    private String determineViewerRole(Order order, CurrentUser currentUser) {
        if (currentUser == null) return "BUYER";
        if (currentUser.id().equals(order.getBuyerId())) {
            return "BUYER";
        }
        if (currentUser.id().equals(order.getSellerId())) {
            return "SELLER";
        }
        return "ADMIN";
    }

    private List<String> calculateAllowedActions(Order order, String viewerRole) {
        List<String> actions = new ArrayList<>();
        if ("BUYER".equals(viewerRole)) {
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                actions.add("PAY");
                actions.add("UPDATE_SHIPPING_INFO");
            } else if (order.getStatus() == OrderStatus.PAID) {
                actions.add("UPDATE_SHIPPING_INFO");
            } else if (order.getStatus() == OrderStatus.SHIPPING) {
                actions.add("CONFIRM_RECEIPT");
            }
        } else if ("SELLER".equals(viewerRole)) {
            if (order.getStatus() == OrderStatus.PAID) {
                actions.add("SHIP");
            }
        }
        return actions;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderUserSummaryResponse getOrderSummary(CurrentUser currentUser) {
        Long userId = currentUser.id();
        var buyerSummary = buildRoleSummary(orderRepository.countByBuyerIdGroupByStatus(userId));
        var sellerSummary = buildRoleSummary(orderRepository.countBySellerIdGroupByStatus(userId));
        return new OrderUserSummaryResponse(buyerSummary, sellerSummary);
    }

    private OrderUserSummaryResponse.RoleSummary buildRoleSummary(List<Object[]> rows) {
        long total = 0;
        long pending = 0;
        long paid = 0;
        long shipping = 0;
        long completed = 0;
        long cancelled = 0;
        long expired = 0;

        for (Object[] row : rows) {
            if (row != null && row.length >= 2 && row[0] != null) {
                OrderStatus status = (OrderStatus) row[0];
                long count = ((Number) row[1]).longValue();
                total += count;
                switch (status) {
                    case PENDING_PAYMENT -> pending = count;
                    case PAID -> paid = count;
                    case SHIPPING -> shipping = count;
                    case COMPLETED -> completed = count;
                    case CANCELLED -> cancelled = count;
                    case EXPIRED -> expired = count;
                }
            }
        }

        return new OrderUserSummaryResponse.RoleSummary(
                total, pending, paid, shipping, completed, cancelled, expired
        );
    }
}
