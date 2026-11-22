package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.common.PageResponse;
import com.omnibid.common.SortWhitelist;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.order.OrderConfirmReceiptResponse;
import com.omnibid.dto.order.OrderDetailResponse;
import com.omnibid.dto.order.OrderSummaryResponse;
import com.omnibid.dto.order.ShipOrderRequest;
import com.omnibid.dto.order.ShipOrderResponse;
import com.omnibid.dto.order.ShippingInfoRequest;
import com.omnibid.dto.user.UserRef;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.entity.ProductImage;
import com.omnibid.entity.User;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Set<String> ALLOWED_ORDER_SORTS = Set.of(
            "createdAt", "finalPrice", "paymentDeadline", "confirmDeadline", "status"
    );

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;
    private final OrderAccessPolicy orderAccessPolicy;
    private final OrderLifecycleSupport orderLifecycleSupport;
    private final AppProperties appProperties;
    private final Clock clock;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public Order createFromAuction(Auction auction, Instant now) {
        return orderRepository.findByAuctionId(auction.getId())
                .orElseGet(() -> {
                    Duration deadline = appProperties.getOrder().getPaymentDeadlineHours();
                    Instant closedAt = auction.getClosedAt() != null
                            ? auction.getClosedAt()
                            : (now != null ? now : Instant.now(clock));
                    Order order = Order.builder()
                            .auctionId(auction.getId())
                            .productId(auction.getProductId())
                            .sellerId(auction.getSellerId())
                            .buyerId(auction.getWinnerId())
                            .finalPrice(auction.getFinalPrice())
                            .status(OrderStatus.PENDING_PAYMENT)
                            .paymentDeadline(closedAt.plus(deadline))
                            .build();
                    log.info("Creating order for auction {}: buyer={}, amount={}, paymentDeadline={}",
                            auction.getId(), auction.getWinnerId(), auction.getFinalPrice(), order.getPaymentDeadline());
                    return orderRepository.save(order);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getOrders(CurrentUser currentUser, String role, OrderStatus status, Pageable pageable) {
        SortWhitelist.validate(pageable, ALLOWED_ORDER_SORTS);

        Page<Order> page;
        if (role == null || role.isBlank()) {
            page = orderRepository.findByBuyerOrSeller(currentUser.id(), status, pageable);
        } else if ("BUYER".equalsIgnoreCase(role.trim())) {
            page = orderRepository.findByBuyerId(currentUser.id(), status, pageable);
        } else if ("SELLER".equalsIgnoreCase(role.trim())) {
            page = orderRepository.findBySellerId(currentUser.id(), status, pageable);
        } else {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Tham số role không hợp lệ",
                    Map.of("fields", Map.of("role", "Chỉ chấp nhận BUYER hoặc SELLER"))
            );
        }

        return PageResponse.from(page, this::mapToOrderSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderById(CurrentUser currentUser, Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng"));

        orderAccessPolicy.requireViewer(order, currentUser);

        return mapToOrderDetailResponse(order, currentUser);
    }

    @Override
    @Transactional
    public OrderDetailResponse updateShippingInfo(CurrentUser currentUser, Long id, ShippingInfoRequest request) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng"));

        orderAccessPolicy.requireBuyer(order, currentUser);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Chỉ được cập nhật thông tin giao hàng khi đơn đang chờ thanh toán"
            );
        }

        order.setShippingAddress(request.getShippingAddress().trim());
        order.setPhoneNumber(request.getPhoneNumber().trim());
        Order saved = orderRepository.save(order);

        return mapToOrderDetailResponse(saved, currentUser);
    }

    @Override
    @Transactional
    public ShipOrderResponse shipOrder(CurrentUser currentUser, Long id, ShipOrderRequest request) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng"));

        orderAccessPolicy.requireSeller(order, currentUser);

        String trackingCode = (request != null && request.getTrackingCode() != null && !request.getTrackingCode().isBlank())
                ? request.getTrackingCode().trim()
                : null;

        Instant now = Instant.now(clock);
        order.markShipped(trackingCode, now, appProperties.getOrder().getConfirmDeadlineDays());
        orderRepository.save(order);

        notificationService.notify(
                order.getBuyerId(),
                NotificationType.ORDER_UPDATE,
                NotificationTemplates.orderShippedTitle(order.getId()),
                NotificationTemplates.orderShippedContent(order.getId(), order.getTrackingCode(), order.getConfirmDeadline()),
                "ORDER",
                order.getId()
        );

        return ShipOrderResponse.builder()
                .id(order.getId())
                .orderStatus(order.getStatus())
                .shippedAt(order.getShippedAt())
                .confirmDeadline(order.getConfirmDeadline())
                .trackingCode(order.getTrackingCode())
                .build();
    }

    @Override
    @Transactional
    public OrderConfirmReceiptResponse confirmReceipt(CurrentUser currentUser, Long id) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng"));

        orderAccessPolicy.requireBuyer(order, currentUser);

        Instant now = Instant.now(clock);
        order.confirmReceived(now);
        orderRepository.save(order);

        Product product = productRepository.findByIdForUpdate(order.getProductId()).orElse(null);
        if (product != null) {
            product.markSold();
            productRepository.save(product);
        }

        notificationService.notify(
                order.getSellerId(),
                NotificationType.ORDER_UPDATE,
                NotificationTemplates.orderReceiptConfirmedTitle(),
                NotificationTemplates.orderReceiptConfirmedContent(order.getId()),
                "ORDER",
                order.getId()
        );

        return OrderConfirmReceiptResponse.builder()
                .id(order.getId())
                .orderStatus(order.getStatus())
                .productStatus(product != null ? product.getStatus() : ProductStatus.SOLD)
                .build();
    }

    @Override
    public void expireOverdueOrders() {
        Instant now = Instant.now(clock);
        List<Long> overdueOrderIds = orderRepository.findIdsByStatusAndPaymentDeadlineLessThanEqual(
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
    public void autoCompleteOrders() {
        Instant now = Instant.now(clock);
        List<Long> overdueOrderIds = orderRepository.findIdsByStatusAndConfirmDeadlineLessThanEqual(
                OrderStatus.SHIPPING, now
        );
        for (Long orderId : overdueOrderIds) {
            try {
                orderLifecycleSupport.autoCompleteOne(orderId, now);
            } catch (Exception ex) {
                log.error("Failed to auto-complete order id={}", orderId, ex);
            }
        }
    }

    private OrderSummaryResponse mapToOrderSummaryResponse(Order order) {
        Product product = productRepository.findById(order.getProductId()).orElse(null);
        String primaryImageUrl = productImageRepository.findByProductIdAndIsPrimaryTrue(order.getProductId())
                .map(ProductImage::getImageUrl)
                .orElse(null);

        UserRef buyer = toUserRef(order.getBuyerId());
        UserRef seller = toUserRef(order.getSellerId());

        return OrderSummaryResponse.builder()
                .id(order.getId())
                .auctionId(order.getAuctionId())
                .productId(order.getProductId())
                .title(product != null ? product.getTitle() : null)
                .primaryImageUrl(primaryImageUrl)
                .finalPrice(order.getFinalPrice())
                .status(order.getStatus())
                .buyer(buyer)
                .seller(seller)
                .paymentDeadline(order.getPaymentDeadline())
                .confirmDeadline(order.getConfirmDeadline())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderDetailResponse mapToOrderDetailResponse(Order order, CurrentUser currentUser) {
        Product product = productRepository.findById(order.getProductId()).orElse(null);
        String primaryImageUrl = productImageRepository.findByProductIdAndIsPrimaryTrue(order.getProductId())
                .map(ProductImage::getImageUrl)
                .orElse(null);

        UserRef buyer = toUserRef(order.getBuyerId());
        UserRef seller = toUserRef(order.getSellerId());
        String viewerRole = currentUser.id().equals(order.getBuyerId()) ? "BUYER" : "SELLER";

        return OrderDetailResponse.builder()
                .id(order.getId())
                .auctionId(order.getAuctionId())
                .productId(order.getProductId())
                .title(product != null ? product.getTitle() : null)
                .primaryImageUrl(primaryImageUrl)
                .finalPrice(order.getFinalPrice())
                .status(order.getStatus())
                .buyer(buyer)
                .seller(seller)
                .paymentDeadline(order.getPaymentDeadline())
                .confirmDeadline(order.getConfirmDeadline())
                .createdAt(order.getCreatedAt())
                .shippingAddress(order.getShippingAddress())
                .phoneNumber(order.getPhoneNumber())
                .trackingCode(order.getTrackingCode())
                .paidAt(order.getPaidAt())
                .shippedAt(order.getShippedAt())
                .completedAt(order.getCompletedAt())
                .cancelledAt(order.getCancelledAt())
                .cancelReason(order.getCancelReason())
                .productStatus(product != null ? product.getStatus() : null)
                .viewerRole(viewerRole)
                .build();
    }

    private UserRef toUserRef(Long userId) {
        if (userId == null) {
            return null;
        }
        String fullName = userRepository.findById(userId).map(User::getFullName).orElse(null);
        return new UserRef(userId, fullName);
    }
}
