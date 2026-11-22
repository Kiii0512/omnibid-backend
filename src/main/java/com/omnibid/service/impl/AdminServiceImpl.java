package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.MaskingUtils;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.admin.*;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.order.OrderResponse;
import com.omnibid.dto.websocket.AuctionEvent;
import com.omnibid.entity.*;
import com.omnibid.enums.*;
import com.omnibid.repository.*;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.AdminService;
import com.omnibid.service.NotificationService;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final BidRepository bidRepository;
    private final ReportRepository reportRepository;
    private final CategoryRepository categoryRepository;
    private final NotificationService notificationService;
    private final WebSocketPublisher webSocketPublisher;
    private final Clock clock;

    @Override
    @Transactional
    public UserStatusUpdateResponse updateUserStatus(CurrentUser admin, Long userId, UserStatusUpdateRequest request) {
        requireAdmin(admin);

        if (request.status() == UserStatus.BANNED) {
            Map<String, Object> effects = banUserInternal(userId, request.reason(), admin.id());
            return new UserStatusUpdateResponse(userId, UserStatus.BANNED, effects);
        } else if (request.status() == UserStatus.ACTIVE) {
            unbanUserInternal(userId, request.reason(), admin.id());
            return new UserStatusUpdateResponse(userId, UserStatus.ACTIVE, Map.of());
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported user status: " + request.status());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getUsers(CurrentUser admin, UserStatus status, String q, Pageable pageable) {
        requireAdmin(admin);

        String searchQuery = (q != null && !q.trim().isEmpty()) ? q.trim() : null;
        Page<User> page = userRepository.findAdminUsers(status, searchQuery, pageable);

        return PageResponse.from(page, u -> new AdminUserResponse(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getPhoneNumber(),
                u.getAvatarUrl(),
                u.getRole(),
                u.getStatus(),
                u.getCreatedAt()
        ));
    }

    private void unbanUserInternal(Long userId, String reason, Long adminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        user.unban();
        userRepository.save(user);
        log.info("Admin {} action=USER_UNBAN target=USER:{} reason={}", adminId, userId, reason);
    }

    private Map<String, Object> banUserInternal(Long userId, String reason, Long adminId) {
        // TODO v4-phase-8: refine BR-17 locking and notifications per BUSINESS_RULES §5.9
        if (userId.equals(adminId)) {
            throw new BusinessException(ErrorCode.CANNOT_BAN_ADMIN, "Cannot ban yourself");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        user.ban();
        userRepository.save(user);

        Instant now = Instant.now(clock);
        List<Long> sellerAuctionIds = auctionRepository.findBySellerIdAndStatusIn(
                userId, List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE)
        ).stream().map(Auction::getId).sorted().toList();

        List<Long> unpaidOrderIds = orderRepository.findUnpaidOrdersByUser(userId)
                .stream().map(Order::getId).sorted().toList();

        List<Long> cancelledAuctionIds = new ArrayList<>();
        SortedSet<Long> productIdsToRelease = new TreeSet<>();

        // Lock order: auctions -> orders -> products
        if (!sellerAuctionIds.isEmpty()) {
            for (Auction auc : auctionRepository.findAllByIdInForUpdateOrderByIdAsc(sellerAuctionIds)) {
                if (auc.isOpen()) {
                    auc.cancel("ACCOUNT_BANNED");
                    auc.setClosedAt(now);
                    auctionRepository.save(auc);
                    cancelledAuctionIds.add(auc.getId());
                    productIdsToRelease.add(auc.getProductId());
                    broadcastAuctionUpdate(auc);
                }
            }
        }

        List<Long> cancelledOrderIds = new ArrayList<>();
        if (!unpaidOrderIds.isEmpty()) {
            for (Order o : orderRepository.findAllByIdInForUpdateOrderByIdAsc(unpaidOrderIds)) {
                if (o.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    o.cancel("ACCOUNT_BANNED", now);
                    orderRepository.save(o);
                    cancelledOrderIds.add(o.getId());
                    if (o.getProductId() != null) {
                        productIdsToRelease.add(o.getProductId());
                    }

                    Long otherPartyId = o.getBuyerId().equals(userId) ? o.getSellerId() : o.getBuyerId();
                    notificationService.notify(otherPartyId, NotificationType.ORDER_UPDATE,
                            NotificationTemplates.orderCancelledTitle(o.getId(), "ACCOUNT_BANNED"),
                            NotificationTemplates.orderCancelledContent(o.getId(), "ACCOUNT_BANNED"),
                            "ORDER", o.getId());
                }
            }
        }

        if (!productIdsToRelease.isEmpty()) {
            for (Product prod : productRepository.findAllByIdInForUpdateOrderByIdAsc(new ArrayList<>(productIdsToRelease))) {
                if (prod.getStatus() == ProductStatus.IN_AUCTION || prod.getStatus() == ProductStatus.RESERVED) {
                    prod.release();
                    productRepository.save(prod);
                }
            }
        }

        log.info("Admin {} action=USER_BAN target=USER:{} reason={} cancelledAuctions={} cancelledOrders={}",
                adminId, userId, reason, cancelledAuctionIds, cancelledOrderIds);

        return Map.of(
                "cancelledAuctionIds", cancelledAuctionIds,
                "cancelledOrderIds", cancelledOrderIds
        );
    }

    @Override
    @Transactional
    public Map<String, Object> banUser(Long userId, String reason, Long adminId) {
        return banUserInternal(userId, reason, adminId);
    }

    @Override
    @Transactional
    public ProductHideResponse hideProduct(CurrentUser admin, Long productId, ProductHideRequest request) {
        requireAdmin(admin);
        // TODO v4-phase-8: remove unhide branch, HIDDEN is terminal per BR-16
        if (request.status() == ProductStatus.HIDDEN) {
            Map<String, Object> effects = hideProductInternal(productId, request.reason(), admin.id());
            @SuppressWarnings("unchecked")
            List<Long> cancelledAuctionIds = (List<Long>) effects.getOrDefault("cancelledAuctionIds", List.of());
            @SuppressWarnings("unchecked")
            List<Long> cancelledOrderIds = (List<Long>) effects.getOrDefault("cancelledOrderIds", List.of());
            return new ProductHideResponse(productId, ProductStatus.HIDDEN, cancelledAuctionIds, cancelledOrderIds);
        }
        throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Only HIDDEN status is supported: " + request.status());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminProductResponse> getProducts(CurrentUser admin, ProductStatus status, Long sellerId, Long categoryId, Pageable pageable) {
        requireAdmin(admin);

        Page<Product> page = productRepository.findAdminProducts(status, sellerId, categoryId, pageable);

        return PageResponse.from(page, p -> {
            String sellerName = userRepository.findById(p.getSellerId()).map(User::getFullName).orElse(null);
            String categoryName = categoryRepository.findById(p.getCategoryId()).map(Category::getName).orElse(null);
            return new AdminProductResponse(
                    p.getId(),
                    p.getSellerId(),
                    sellerName,
                    p.getCategoryId(),
                    categoryName,
                    p.getTitle(),
                    p.getDescription(),
                    p.getCondition(),
                    p.getStatus(),
                    p.getCreatedAt()
            );
        });
    }

    private Map<String, Object> hideProductInternal(Long productId, String reason, Long adminId) {
        // Lock order: auctions -> orders -> products
        List<Long> openAuctionIds = auctionRepository.findByProductIdAndStatusIn(
                productId, List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE)
        ).stream().map(Auction::getId).sorted().toList();

        List<Long> productOrderIds = orderRepository.findByProductIdAndStatusIn(
                productId, List.of(OrderStatus.PENDING_PAYMENT)
        ).stream().map(Order::getId).sorted().toList();

        Instant now = Instant.now(clock);
        List<Long> cancelledAuctionIds = new ArrayList<>();
        if (!openAuctionIds.isEmpty()) {
            for (Auction auc : auctionRepository.findAllByIdInForUpdateOrderByIdAsc(openAuctionIds)) {
                if (auc.isOpen()) {
                    auc.cancel("PRODUCT_HIDDEN");
                    auc.setClosedAt(now);
                    auctionRepository.save(auc);
                    cancelledAuctionIds.add(auc.getId());
                    broadcastAuctionUpdate(auc);
                }
            }
        }

        List<Long> cancelledOrderIds = new ArrayList<>();
        if (!productOrderIds.isEmpty()) {
            for (Order ord : orderRepository.findAllByIdInForUpdateOrderByIdAsc(productOrderIds)) {
                if (ord.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    ord.cancel("PRODUCT_HIDDEN", now);
                    orderRepository.save(ord);
                    cancelledOrderIds.add(ord.getId());

                    notificationService.notify(ord.getBuyerId(), NotificationType.ORDER_UPDATE,
                            NotificationTemplates.orderCancelledTitle(ord.getId(), "PRODUCT_HIDDEN"),
                            NotificationTemplates.orderCancelledContent(ord.getId(), "PRODUCT_HIDDEN"),
                            "ORDER", ord.getId());
                    notificationService.notify(ord.getSellerId(), NotificationType.ORDER_UPDATE,
                            NotificationTemplates.orderCancelledTitle(ord.getId(), "PRODUCT_HIDDEN"),
                            NotificationTemplates.orderCancelledContent(ord.getId(), "PRODUCT_HIDDEN"),
                            "ORDER", ord.getId());
                }
            }
        }

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Product not found"));
        product.hide();
        productRepository.save(product);

        log.info("Admin {} action=PRODUCT_HIDE target=PRODUCT:{} reason={} cancelledAuctions={} cancelledOrders={}",
                adminId, productId, reason, cancelledAuctionIds, cancelledOrderIds);

        return Map.of(
                "productId", productId,
                "productStatus", "HIDDEN",
                "cancelledAuctionIds", cancelledAuctionIds,
                "cancelledOrderIds", cancelledOrderIds
        );
    }

    @Override
    @Transactional
    public Map<String, Object> hideProduct(Long productId, String reason, Long adminId) {
        return hideProductInternal(productId, reason, adminId);
    }

    @Override
    @Transactional
    public AuctionResponse cancelAuction(CurrentUser admin, Long auctionId, AdminCancelAuctionRequest request) {
        requireAdmin(admin);
        Auction auction = cancelAuctionInternal(auctionId, request.reason(), admin.id());
        return mapToAuctionResponse(auction);
    }

    private Auction cancelAuctionInternal(Long auctionId, String reason, Long adminId) {
        // Lock order: auctions -> products
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Auction not found"));

        Product product = productRepository.findByIdForUpdate(auction.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Product not found"));

        Instant now = Instant.now(clock);
        auction.cancel(reason);
        auction.setClosedAt(now);
        auctionRepository.save(auction);

        product.release();
        productRepository.save(product);

        log.info("Admin {} action=AUCTION_CANCEL target=AUCTION:{} reason={}", adminId, auctionId, reason);

        notificationService.notify(auction.getSellerId(), NotificationType.AUCTION_CANCELLED,
                NotificationTemplates.auctionCancelledTitle(reason),
                NotificationTemplates.auctionCancelledContent(auction.getId(), reason),
                "AUCTION", auction.getId());

        if (auction.getHighestBidderId() != null) {
            notificationService.notify(auction.getHighestBidderId(), NotificationType.AUCTION_CANCELLED,
                    NotificationTemplates.auctionCancelledTitle(reason),
                    NotificationTemplates.auctionCancelledContent(auction.getId(), reason),
                    "AUCTION", auction.getId());
        }

        broadcastAuctionUpdate(auction);
        return auction;
    }

    @Override
    @Transactional
    public Map<String, Object> cancelAuction(Long auctionId, String reason, Long adminId) {
        cancelAuctionInternal(auctionId, reason, adminId);
        return Map.of("auctionId", auctionId, "auctionStatus", "CANCELLED");
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(CurrentUser admin, Long orderId, AdminCancelOrderRequest request) {
        requireAdmin(admin);
        Order order = cancelOrderInternal(orderId, request.reason(), admin.id());
        return mapToOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummaryResponse> getOrders(CurrentUser admin, OrderStatus status, Long buyerId, Long sellerId, Instant from, Instant to, Pageable pageable) {
        requireAdmin(admin);

        Page<Order> page = orderRepository.findAdminOrders(status, buyerId, sellerId, from, to, pageable);

        return PageResponse.from(page, o -> {
            String buyerName = userRepository.findById(o.getBuyerId()).map(User::getFullName).orElse(null);
            String sellerName = userRepository.findById(o.getSellerId()).map(User::getFullName).orElse(null);
            String productTitle = productRepository.findById(o.getProductId()).map(Product::getTitle).orElse(null);
            String paymentStatus = paymentRepository.findByOrderIdOrderByAttemptNoDesc(o.getId())
                    .stream().findFirst().map(p -> p.getStatus().name()).orElse(null);

            return new AdminOrderSummaryResponse(
                    o.getId(),
                    o.getAuctionId(),
                    o.getProductId(),
                    productTitle,
                    o.getBuyerId(),
                    buyerName,
                    o.getSellerId(),
                    sellerName,
                    o.getFinalPrice(),
                    o.getStatus(),
                    paymentStatus,
                    o.getCreatedAt()
            );
        });
    }

    private Order cancelOrderInternal(Long orderId, String reason, Long adminId) {
        // Lock order: orders -> products
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Order not found"));

        Product product = productRepository.findByIdForUpdate(order.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Product not found"));

        Instant now = Instant.now(clock);
        order.cancel(reason, now);
        orderRepository.save(order);

        product.release();
        productRepository.save(product);

        log.info("Admin {} action=ORDER_CANCEL target=ORDER:{} reason={}", adminId, order.getId(), reason);

        notificationService.notify(order.getBuyerId(), NotificationType.ORDER_UPDATE,
                NotificationTemplates.orderCancelledTitle(order.getId(), reason),
                NotificationTemplates.orderCancelledContent(order.getId(), reason),
                "ORDER", order.getId());

        notificationService.notify(order.getSellerId(), NotificationType.ORDER_UPDATE,
                NotificationTemplates.orderCancelledTitle(order.getId(), reason),
                NotificationTemplates.orderCancelledContent(order.getId(), reason),
                "ORDER", order.getId());

        return order;
    }

    @Override
    @Transactional
    public Map<String, Object> cancelOrder(Long orderId, String reason, Long adminId) {
        Order order = cancelOrderInternal(orderId, reason, adminId);
        Product product = productRepository.findById(order.getProductId()).orElse(null);
        String prodStatus = product != null ? product.getStatus().name() : "AVAILABLE";
        return Map.of("orderStatus", "CANCELLED", "productStatus", prodStatus);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStatisticsResponse getStatistics(CurrentUser admin, Instant from, Instant to) {
        // TODO v4-phase-8: rewrite cumulative statistics per FR-20
        requireAdmin(admin);

        Instant effectiveTo = to != null ? to : Instant.now(clock);
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(30, ChronoUnit.DAYS);

        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        long bannedUsers = userRepository.countByStatus(UserStatus.BANNED);
        long newUsersInRange = userRepository.countByCreatedAtBetween(effectiveFrom, effectiveTo);
        AdminStatisticsResponse.UserStats userStats = new AdminStatisticsResponse.UserStats(totalUsers, activeUsers, bannedUsers, newUsersInRange);

        Map<String, Long> productStats = new LinkedHashMap<>();
        for (ProductStatus ps : ProductStatus.values()) {
            productStats.put(ps.name(), productRepository.countByStatus(ps));
        }

        long scheduledAuctions = auctionRepository.countByStatus(AuctionStatus.SCHEDULED);
        long activeAuctions = auctionRepository.countByStatus(AuctionStatus.ACTIVE);
        long endedAuctions = auctionRepository.countByStatus(AuctionStatus.ENDED);
        long unsoldAuctions = auctionRepository.countByStatus(AuctionStatus.UNSOLD);
        long cancelledAuctions = auctionRepository.countByStatus(AuctionStatus.CANCELLED);
        long closedAuctionsTotal = endedAuctions + unsoldAuctions;
        double sellThroughRate = closedAuctionsTotal > 0
                ? Math.round(((double) endedAuctions / closedAuctionsTotal) * 100.0) / 100.0
                : 0.0;
        AdminStatisticsResponse.AuctionStats auctionStats = new AdminStatisticsResponse.AuctionStats(
                scheduledAuctions, activeAuctions, endedAuctions, unsoldAuctions, cancelledAuctions, sellThroughRate
        );

        long totalBids = bidRepository.count();
        long bidsInRange = bidRepository.countByCreatedAtBetween(effectiveFrom, effectiveTo);
        long totalAuctions = scheduledAuctions + activeAuctions + endedAuctions + unsoldAuctions + cancelledAuctions;
        double avgPerAuction = totalAuctions > 0
                ? Math.round(((double) totalBids / totalAuctions) * 10.0) / 10.0
                : 0.0;
        AdminStatisticsResponse.BidStats bidStats = new AdminStatisticsResponse.BidStats(totalBids, bidsInRange, avgPerAuction);

        Map<String, Long> orderStats = new LinkedHashMap<>();
        for (OrderStatus os : OrderStatus.values()) {
            orderStats.put(os.name(), orderRepository.countByStatus(os));
        }

        BigDecimal completedTotal = orderRepository.sumCompletedRevenue(effectiveFrom, effectiveTo);
        AdminStatisticsResponse.RevenueStats revenueStats = new AdminStatisticsResponse.RevenueStats(true, completedTotal);

        long successPayments = paymentRepository.countByStatus(PaymentStatus.SUCCESS);
        long failedPayments = paymentRepository.countByStatus(PaymentStatus.FAILED);
        long resolvedPaymentsTotal = successPayments + failedPayments;
        double successRate = resolvedPaymentsTotal > 0
                ? Math.round(((double) successPayments / resolvedPaymentsTotal) * 100.0) / 100.0
                : 0.0;
        AdminStatisticsResponse.PaymentStats paymentStats = new AdminStatisticsResponse.PaymentStats(
                successPayments, failedPayments, 0L, successRate
        );

        Map<String, Long> reportStats = new LinkedHashMap<>();
        for (ReportStatus rs : ReportStatus.values()) {
            reportStats.put(rs.name(), reportRepository.countByStatus(rs));
        }

        return new AdminStatisticsResponse(
                new AdminStatisticsResponse.RangeDto(effectiveFrom, effectiveTo),
                userStats,
                productStats,
                auctionStats,
                bidStats,
                orderStats,
                revenueStats,
                paymentStats,
                reportStats
        );
    }

    private void requireAdmin(CurrentUser admin) {
        if (!admin.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Admin required");
        }
    }

    private void broadcastAuctionUpdate(Auction auction) {
        try {
            String maskedLeaderName = auction.getHighestBidderId() != null
                    ? userRepository.findById(auction.getHighestBidderId())
                            .map(User::getFullName)
                            .map(MaskingUtils::maskName)
                            .orElse(null)
                    : null;
            webSocketPublisher.publishAuctionEvent(
                    AuctionEvent.of(AuctionEventType.AUCTION_CANCELLED, auction, maskedLeaderName, null, Instant.now(clock))
            );
        } catch (Exception e) {
            log.warn("Failed to broadcast WebSocket update for auction {}: {}", auction.getId(), e.getMessage());
        }
    }

    private AuctionResponse mapToAuctionResponse(Auction auction) {
        String sellerName = userRepository.findById(auction.getSellerId()).map(User::getFullName).orElse(null);
        String productTitle = productRepository.findById(auction.getProductId()).map(Product::getTitle).orElse(null);
        String highestBidderName = auction.getHighestBidderId() != null
                ? userRepository.findById(auction.getHighestBidderId()).map(User::getFullName).orElse(null)
                : null;

        return AuctionResponse.builder()
                .id(auction.getId())
                .productId(auction.getProductId())
                .productTitle(productTitle)
                .sellerId(auction.getSellerId())
                .sellerName(sellerName)
                .startPrice(auction.getStartPrice())
                .bidIncrement(auction.getBidIncrement())
                .currentPrice(auction.getCurrentPrice())
                .highestBidderId(auction.getHighestBidderId())
                .highestBidderName(highestBidderName)
                .bidCount(auction.getBidCount())
                .startTime(auction.getStartTime())
                .endTime(auction.getEndTime())
                .status(auction.getStatus())
                .winnerId(auction.getWinnerId())
                .finalPrice(auction.getFinalPrice())
                .closedAt(auction.getClosedAt())
                .cancelReason(auction.getCancelReason())
                .createdAt(auction.getCreatedAt())
                .build();
    }

    private OrderResponse mapToOrderResponse(Order order) {
        String productTitle = productRepository.findById(order.getProductId()).map(Product::getTitle).orElse(null);
        return OrderResponse.builder()
                .id(order.getId())
                .auctionId(order.getAuctionId())
                .productId(order.getProductId())
                .productTitle(productTitle)
                .finalPrice(order.getFinalPrice())
                .status(order.getStatus())
                .paymentDeadline(order.getPaymentDeadline())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
