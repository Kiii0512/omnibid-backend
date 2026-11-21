package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.MaskingUtils;
import com.omnibid.common.PageResponse;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.order.*;
import com.omnibid.entity.*;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.ReportStatus;
import com.omnibid.repository.*;
import com.omnibid.security.CurrentUser;
import com.omnibid.security.OrderAccessPolicy;
import com.omnibid.service.NotificationService;
import com.omnibid.service.OrderLifecycleSupport;
import com.omnibid.service.OrderService;
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
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final ReportRepository reportRepository;
    private final OrderAccessPolicy orderAccessPolicy;
    private final OrderLifecycleSupport orderLifecycleSupport;
    private final AppProperties appProperties;
    private final Clock clock;
    private NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired
    public OrderServiceImpl(OrderRepository orderRepository, ProductRepository productRepository,
                            ProductImageRepository productImageRepository, UserRepository userRepository,
                            PaymentRepository paymentRepository, ReportRepository reportRepository,
                            OrderAccessPolicy orderAccessPolicy, OrderLifecycleSupport orderLifecycleSupport,
                            AppProperties appProperties, Clock clock, NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.reportRepository = reportRepository;
        this.orderAccessPolicy = orderAccessPolicy;
        this.orderLifecycleSupport = orderLifecycleSupport;
        this.appProperties = appProperties;
        this.clock = clock;
        this.notificationService = notificationService;
    }

    public OrderServiceImpl(OrderRepository orderRepository, ProductRepository productRepository,
                            ProductImageRepository productImageRepository, UserRepository userRepository,
                            PaymentRepository paymentRepository, ReportRepository reportRepository,
                            OrderAccessPolicy orderAccessPolicy, OrderLifecycleSupport orderLifecycleSupport,
                            AppProperties appProperties, Clock clock) {
        this(orderRepository, productRepository, productImageRepository, userRepository, paymentRepository, reportRepository, orderAccessPolicy, orderLifecycleSupport, appProperties, clock, null);
    }

    public OrderServiceImpl(OrderRepository orderRepository, AppProperties appProperties) {
        this(orderRepository, null, null, null, null, null, null, null, appProperties, Clock.systemUTC());
    }

    @Override
    @Transactional
    public Order createFromAuction(Auction auction) {
        return orderRepository.findByAuctionId(auction.getId())
                .orElseGet(() -> {
                    int deadlineHours = appProperties.getOrder().getPaymentDeadlineHours();
                    Instant closedAt = auction.getClosedAt() != null ? auction.getClosedAt() : Instant.now(clock);
                    Order order = Order.builder()
                            .auctionId(auction.getId())
                            .productId(auction.getProductId())
                            .sellerId(auction.getSellerId())
                            .buyerId(auction.getWinnerId())
                            .finalPrice(auction.getFinalPrice())
                            .status(OrderStatus.PENDING_PAYMENT)
                            .paymentDeadline(closedAt.plus(Duration.ofHours(deadlineHours)))
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

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Cannot update shipping info when order is " + order.getStatus());
        }

        order.setShippingAddress(request.getShippingAddress());
        order.setPhoneNumber(request.getPhoneNumber());
        Order saved = orderRepository.save(order);

        return mapToOrderDetailResponse(saved, currentUser);
    }

    @Override
    @Transactional
    public OrderDetailResponse shipOrder(CurrentUser currentUser, Long id, ShipOrderRequest request) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Order not found with id: " + id));

        orderAccessPolicy.requireSeller(order, currentUser);

        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Đơn hàng phải ở trạng thái PAID để giao hàng");
        }

        int confirmWindowDays = appProperties.getOrder().getConfirmDeadlineDays();
        Instant now = Instant.now(clock);
        order.markShipped(request != null ? request.trackingCode() : null, now, Duration.ofDays(confirmWindowDays));
        Order saved = orderRepository.save(order);

        // TODO v4-phase-7: Notify buyer after commit
        log.info("Order {} marked as SHIPPING by seller {}", id, currentUser.id());
        return mapToOrderDetailResponse(saved, currentUser);
    }

    @Override
    @Transactional
    public OrderConfirmReceiptResponse confirmReceipt(CurrentUser currentUser, Long id) {
        Order order = orderRepository.findByIdForUpdate(id).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng")
        );

        orderAccessPolicy.requireBuyer(order, currentUser);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ORDER_CANCELLED, "Đơn hàng đã bị hủy");
        }

        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Đơn hàng đã hoàn tất");
        }

        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Đơn hàng chưa ở trạng thái đang giao hàng");
        }

        if (reportRepository != null && reportRepository.existsByReportedOrderIdAndStatus(order.getId(), ReportStatus.PENDING)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Đơn hàng đang có khiếu nại chờ xử lý");
        }

        Instant now = Instant.now(clock);
        order.confirmReceived(now);
        orderRepository.save(order);

        Product product = productRepository != null
                ? productRepository.findByIdForUpdate(order.getProductId()).orElse(null)
                : null;
        if (product != null) {
            product.markSold();
            productRepository.save(product);
        }

        // TODO v4-phase-7: Notify seller after commit
        return OrderConfirmReceiptResponse.builder()
                .id(order.getId())
                .orderStatus(order.getStatus())
                .productStatus(product != null ? product.getStatus() : ProductStatus.SOLD)
                .build();
    }

    @Override
    public void sendPaymentReminders() {
        // v4: Nhắc hạn thanh toán đã được gỡ bỏ khỏi đặc tả
    }

    @Override
    public void expireOverdueOrders() {
        Instant now = Instant.now(clock);
        List<Order> overdueOrders = orderRepository.findByStatusAndPaymentDeadlineLessThanEqual(
                OrderStatus.PENDING_PAYMENT, now
        );

        for (Order o : overdueOrders) {
            try {
                orderLifecycleSupport.expireOne(o.getId(), now);
            } catch (Exception ex) {
                log.error("Failed to expire order {}", o.getId(), ex);
            }
        }
    }

    @Override
    public void autoCompleteOrders() {
        Instant now = Instant.now(clock);
        List<Order> overdueOrders = orderRepository.findByStatusAndConfirmDeadlineLessThanEqual(OrderStatus.SHIPPING, now);
        for (Order o : overdueOrders) {
            try {
                orderLifecycleSupport.autoCompleteOne(o.getId(), now);
            } catch (Exception ex) {
                log.error("Failed to auto-complete order id={}", o.getId(), ex);
            }
        }
    }

    private String getProductTitle(Long productId) {
        return productRepository != null ? productRepository.findById(productId).map(Product::getTitle).orElse(null) : null;
    }

    private String getProductImageUrl(Long productId) {
        return productImageRepository != null ? productImageRepository.findByProductIdAndIsPrimaryTrue(productId).map(ProductImage::getImageUrl).orElse(null) : null;
    }

    private OrderResponse mapToOrderResponse(Order order, CurrentUser currentUser) {
        String productTitle = getProductTitle(order.getProductId());
        String productImageUrl = getProductImageUrl(order.getProductId());
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
        String productTitle = getProductTitle(order.getProductId());
        String productImageUrl = getProductImageUrl(order.getProductId());
        String sellerName = null;
        String buyerName = null;

        if (userRepository != null) {
            User seller = userRepository.findById(order.getSellerId()).orElse(null);
            if (seller != null) {
                sellerName = MaskingUtils.maskName(seller.getFullName());
            }
            User buyer = userRepository.findById(order.getBuyerId()).orElse(null);
            if (buyer != null) {
                buyerName = MaskingUtils.maskName(buyer.getFullName());
            }
        }

        OrderDetailResponse.PaymentSummaryDto latestPaymentDto = null;
        if (paymentRepository != null) {
            Payment latestPayment = paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(order.getId()).orElse(null);
            if (latestPayment != null) {
                latestPaymentDto = OrderDetailResponse.PaymentSummaryDto.builder()
                        .paymentId(latestPayment.getId())
                        .attemptNo(latestPayment.getAttemptNo())
                        .status(latestPayment.getStatus())
                        .failureReason(latestPayment.getFailureReason())
                        .build();
            }
        }

        boolean hasPendingComplaint = false;
        if (reportRepository != null) {
            hasPendingComplaint = reportRepository.existsByReportedOrderIdAndStatus(order.getId(), ReportStatus.PENDING);
        }

        String viewerRole = determineViewerRole(order, currentUser);
        List<String> allowedActions = calculateAllowedActions(order, viewerRole, hasPendingComplaint);

        return OrderDetailResponse.builder()
                .id(order.getId()).auctionId(order.getAuctionId()).productId(order.getProductId())
                .productTitle(productTitle).productImageUrl(productImageUrl)
                .sellerId(order.getSellerId()).sellerName(sellerName).buyerId(order.getBuyerId()).buyerName(buyerName)
                .finalPrice(order.getFinalPrice()).status(order.getStatus())
                .shippingAddress(order.getShippingAddress()).phoneNumber(order.getPhoneNumber())
                .trackingCode(order.getTrackingCode()).paymentDeadline(order.getPaymentDeadline())
                .paidAt(order.getPaidAt()).shippedAt(order.getShippedAt()).confirmDeadline(order.getConfirmDeadline())
                .completedAt(order.getCompletedAt()).cancelledAt(order.getCancelledAt()).cancelReason(order.getCancelReason())
                .latestPayment(latestPaymentDto).hasPendingComplaint(hasPendingComplaint)
                .viewerRole(viewerRole).allowedActions(allowedActions).build();
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

    private List<String> calculateAllowedActions(Order order, String viewerRole, boolean hasPendingComplaint) {
        List<String> actions = new ArrayList<>();
        if ("BUYER".equals(viewerRole)) {
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                actions.add("PAY");
                actions.add("UPDATE_SHIPPING_INFO");
            } else if (order.getStatus() == OrderStatus.PAID) {
                actions.add("UPDATE_SHIPPING_INFO");
                if (!hasPendingComplaint) actions.add("COMPLAIN");
            } else if (order.getStatus() == OrderStatus.SHIPPING && !hasPendingComplaint) {
                actions.add("CONFIRM_RECEIPT");
                actions.add("COMPLAIN");
            }
        } else if ("SELLER".equals(viewerRole) && order.getStatus() == OrderStatus.PAID) {
            actions.add("SHIP");
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
                long count = ((Number) row[1]).longValue();
                total += count;
                switch ((OrderStatus) row[0]) {
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
