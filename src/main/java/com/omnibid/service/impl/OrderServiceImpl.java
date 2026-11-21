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
import org.springframework.beans.factory.annotation.Autowired;
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
    private com.omnibid.common.AfterCommitExecutor afterCommitExecutor;

    @Autowired
    public void setAfterCommitExecutor(com.omnibid.common.AfterCommitExecutor afterCommitExecutor) {
        this.afterCommitExecutor = afterCommitExecutor;
    }

    @Autowired
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
        return createFromAuction(auction, Instant.now(clock));
    }

    @Override
    @Transactional
    public Order createFromAuction(Auction auction, Instant closedAt) {
        return orderRepository.findByAuctionId(auction.getId())
                .orElseGet(() -> {
                    int deadlineHours = appProperties.getOrder().getPaymentDeadlineHours();
                    Instant effectiveClosedAt = closedAt != null ? closedAt : Instant.now(clock);
                    Order order = Order.builder()
                            .auctionId(auction.getId())
                            .productId(auction.getProductId())
                            .sellerId(auction.getSellerId())
                            .buyerId(auction.getWinnerId())
                            .finalPrice(auction.getFinalPrice())
                            .status(OrderStatus.PENDING_PAYMENT)
                            .paymentDeadline(effectiveClosedAt.plus(Duration.ofHours(deadlineHours)))
                            .build();
                    log.info("Creating order for auction {}: buyer={}, amount={}",
                            auction.getId(), auction.getWinnerId(), auction.getFinalPrice());
                    return orderRepository.save(order);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getOrders(CurrentUser currentUser, String role, OrderStatus status, Pageable pageable) {
        if (currentUser.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Admin phải sử dụng API quản trị để xem danh sách đơn hàng");
        }
        Page<Order> page;
        if ("buyer".equalsIgnoreCase(role)) {
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
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng với id: " + id));

        orderAccessPolicy.requireViewer(order, currentUser);

        return mapToOrderDetailResponse(order, currentUser);
    }

    @Override
    @Transactional
    public OrderDetailResponse updateShippingInfo(CurrentUser currentUser, Long id, ShippingInfoRequest request) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng với id: " + id));

        orderAccessPolicy.requireBuyer(order, currentUser);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ORDER_CANCELLED, "Đơn hàng đã bị hủy");
        }
        if (order.getStatus() == OrderStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.ORDER_EXPIRED, "Đơn hàng đã hết hạn");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Chỉ được cập nhật thông tin giao hàng khi đơn ở trạng thái PENDING_PAYMENT");
        }

        order.setShippingAddress(request.getShippingAddress());
        order.setPhoneNumber(request.getPhoneNumber());
        Order saved = orderRepository.save(order);

        return mapToOrderDetailResponse(saved, currentUser);
    }

    @Override
    @Transactional
    public OrderShipResponse shipOrder(CurrentUser currentUser, Long id, ShipOrderRequest request) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng với id: " + id));

        orderAccessPolicy.requireSeller(order, currentUser);

        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Đơn hàng phải ở trạng thái PAID để giao hàng");
        }

        int confirmWindowDays = appProperties.getOrder().getConfirmDeadlineDays();
        Instant now = Instant.now(clock);
        order.markShipped(request != null ? request.trackingCode() : null, now, Duration.ofDays(confirmWindowDays));
        Order saved = orderRepository.save(order);

        Long bId = saved.getBuyerId();
        Long oId = saved.getId();
        String trk = saved.getTrackingCode();
        if (afterCommitExecutor != null && notificationService != null) {
            afterCommitExecutor.execute(() -> notificationService.notify(
                    bId,
                    com.omnibid.enums.NotificationType.ORDER_UPDATE,
                    com.omnibid.common.NotificationTemplates.orderShippedBuyerTitle(oId),
                    com.omnibid.common.NotificationTemplates.orderShippedBuyerContent(oId, trk),
                    "ORDER",
                    oId
            ));
        }

        log.info("Order {} marked as SHIPPING by seller {}", id, currentUser.id());
        return OrderShipResponse.builder()
                .id(saved.getId())
                .orderStatus(saved.getStatus())
                .shippedAt(saved.getShippedAt())
                .confirmDeadline(saved.getConfirmDeadline())
                .build();
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
        Long sId = order.getSellerId();
        Long oId = order.getId();
        if (afterCommitExecutor != null && notificationService != null) {
            afterCommitExecutor.execute(() -> notificationService.notify(
                    sId,
                    com.omnibid.enums.NotificationType.ORDER_UPDATE,
                    com.omnibid.common.NotificationTemplates.orderConfirmedSellerTitle(oId),
                    com.omnibid.common.NotificationTemplates.orderConfirmedSellerContent(oId),
                    "ORDER",
                    oId
            ));
        }
        return OrderConfirmReceiptResponse.builder()
                .id(order.getId())
                .orderStatus(order.getStatus())
                .productStatus(product != null ? product.getStatus() : ProductStatus.SOLD)
                .build();
    }

    @Override
    public void expireOverdueOrders() {
        Instant now = Instant.now(clock);
        List<Long> ids = orderRepository.findIdsByStatusAndPaymentDeadlineLessThanEqual(OrderStatus.PENDING_PAYMENT, now);
        for (Long id : ids) {
            try {
                orderLifecycleSupport.expireOne(id, now);
            } catch (Exception ex) {
                log.error("Failed to expire order {}", id, ex);
            }
        }
    }

    @Override
    public void autoCompleteOrders() {
        Instant now = Instant.now(clock);
        List<Long> ids = orderRepository.findIdsByStatusAndConfirmDeadlineLessThanEqual(OrderStatus.SHIPPING, now);
        for (Long id : ids) {
            try {
                orderLifecycleSupport.autoCompleteOne(id, now);
            } catch (Exception ex) {
                log.error("Failed to auto-complete order id={}", id, ex);
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
        return OrderResponse.builder()
                .id(order.getId())
                .auctionId(order.getAuctionId())
                .productId(order.getProductId())
                .productTitle(getProductTitle(order.getProductId()))
                .productImageUrl(getProductImageUrl(order.getProductId()))
                .finalPrice(order.getFinalPrice())
                .status(order.getStatus())
                .viewerRole(determineViewerRole(order, currentUser))
                .paymentDeadline(order.getPaymentDeadline())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderDetailResponse mapToOrderDetailResponse(Order order, CurrentUser currentUser) {
        String sellerName = null;
        String buyerName = null;

        if (userRepository != null) {
            sellerName = userRepository.findById(order.getSellerId()).map(u -> MaskingUtils.maskName(u.getFullName())).orElse(null);
            buyerName = userRepository.findById(order.getBuyerId()).map(u -> MaskingUtils.maskName(u.getFullName())).orElse(null);
        }

        OrderDetailResponse.PaymentSummaryDto latestPaymentDto = null;
        if (paymentRepository != null) {
            Payment latest = paymentRepository.findTopByOrderIdOrderByAttemptNoDesc(order.getId()).orElse(null);
            if (latest != null) {
                latestPaymentDto = OrderDetailResponse.PaymentSummaryDto.builder()
                        .paymentId(latest.getId())
                        .attemptNo(latest.getAttemptNo())
                        .status(latest.getStatus())
                        .failureReason(latest.getFailureReason())
                        .build();
            }
        }

        boolean hasPendingComplaint = reportRepository != null && reportRepository.existsByReportedOrderIdAndStatus(order.getId(), ReportStatus.PENDING);
        String viewerRole = determineViewerRole(order, currentUser);
        List<String> allowedActions = calculateAllowedActions(order, viewerRole, hasPendingComplaint);

        return OrderDetailResponse.builder()
                .id(order.getId()).auctionId(order.getAuctionId()).productId(order.getProductId())
                .productTitle(getProductTitle(order.getProductId())).productImageUrl(getProductImageUrl(order.getProductId()))
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
        if (currentUser.id().equals(order.getBuyerId())) return "BUYER";
        if (currentUser.id().equals(order.getSellerId())) return "SELLER";
        return "ADMIN";
    }

    private List<String> calculateAllowedActions(Order order, String viewerRole, boolean hasPendingComplaint) {
        List<String> actions = new ArrayList<>();
        if ("BUYER".equals(viewerRole)) {
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                actions.add("PAY");
                actions.add("UPDATE_SHIPPING_INFO");
            } else if (order.getStatus() == OrderStatus.SHIPPING) {
                actions.add("CONFIRM_RECEIPT");
            }
        } else if ("SELLER".equals(viewerRole) && order.getStatus() == OrderStatus.PAID) {
            actions.add("SHIP");
        }
        return actions;
    }
}
