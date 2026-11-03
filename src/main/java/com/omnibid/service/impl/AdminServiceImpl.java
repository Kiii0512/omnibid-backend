package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.admin.*;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.order.OrderResponse;
import com.omnibid.dto.websocket.AuctionWebSocketMessage;
import com.omnibid.entity.*;
import com.omnibid.enums.*;
import com.omnibid.repository.*;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.AdminService;
import com.omnibid.service.AuditService;
import com.omnibid.service.NotificationService;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
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
    private final ShipmentRepository shipmentRepository;
    private final ReportRepository reportRepository;
    private final AuditLogRepository auditLogRepository;
    private final CategoryRepository categoryRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final WebSocketPublisher webSocketPublisher;
    private final Clock clock;

    // =========================================================================
    // USER MANAGEMENT (ADM-01, 02..06)
    // =========================================================================

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
            throw new AppException("VALIDATION_ERROR", "Unsupported user status: " + request.status(), HttpStatus.BAD_REQUEST);
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
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));

        if (user.getStatus() != UserStatus.BANNED) {
            throw new AppException("INVALID_STATUS_TRANSITION", "User is not banned", HttpStatus.CONFLICT);
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        auditService.log(adminId, "USER_UNBAN", "USER", userId, reason);
    }

    private Map<String, Object> banUserInternal(Long userId, String reason, Long adminId) {
        if (userId.equals(adminId)) {
            throw new AppException("CANNOT_BAN_ADMIN", "Cannot ban yourself", HttpStatus.CONFLICT);
        }

        // Lock hierarchy: L1 User
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));

        if (user.getRole() == UserRole.ADMIN) {
            throw new AppException("CANNOT_BAN_ADMIN", "Cannot ban an admin user", HttpStatus.CONFLICT);
        }

        if (user.getStatus() == UserStatus.BANNED) {
            throw new AppException("INVALID_STATUS_TRANSITION", "User is already banned", HttpStatus.CONFLICT);
        }

        user.setStatus(UserStatus.BANNED);
        userRepository.save(user);

        // ADM-03: Invalidate user's bids in ACTIVE auctions (lock auctions by id ASC)
        List<Long> auctionIdsWithBids = bidRepository.findDistinctAuctionIdsByBidderIdAndStatusIn(
                userId, List.of(BidStatus.LEADING, BidStatus.OUTBID)
        ).stream().sorted().toList();

        int invalidatedBids = 0;
        List<Long> affectedAuctionIds = new ArrayList<>();

        for (Long auctionId : auctionIdsWithBids) {
            // Lock hierarchy: L2 Auction
            Auction auction = auctionRepository.findByIdForUpdate(auctionId).orElse(null);
            if (auction == null) continue;
            if (auction.getStatus() != AuctionStatus.ACTIVE) continue;

            List<Bid> userBids = bidRepository.findByAuctionIdAndBidderId(auctionId, userId);
            for (Bid b : userBids) {
                if (b.getStatus() == BidStatus.LEADING || b.getStatus() == BidStatus.OUTBID) {
                    b.setStatus(BidStatus.INVALIDATED);
                    b.setInvalidReason("ACCOUNT_BANNED");
                    bidRepository.save(b);
                    invalidatedBids++;
                }
            }
            affectedAuctionIds.add(auctionId);

            // If user was highest bidder, reselect leader
            if (auction.getHighestBidderId() != null && auction.getHighestBidderId().equals(userId)) {
                List<Bid> remainingBids = bidRepository.findValidBidsDesc(
                        auctionId, List.of(BidStatus.LEADING, BidStatus.OUTBID), PageRequest.of(0, 1)
                );
                if (!remainingBids.isEmpty()) {
                    Bid newLeader = remainingBids.get(0);
                    newLeader.setStatus(BidStatus.LEADING);
                    bidRepository.save(newLeader);
                    auction.setCurrentPrice(newLeader.getAmount());
                    auction.setHighestBidderId(newLeader.getBidderId());
                } else {
                    auction.setCurrentPrice(auction.getStartPrice());
                    auction.setHighestBidderId(null);
                }
            }

            // Recalculate bidCount
            List<Bid> allValidBids = bidRepository.findValidBidsDesc(
                    auctionId, List.of(BidStatus.LEADING, BidStatus.OUTBID), Pageable.unpaged()
            );
            auction.setBidCount(allValidBids.size());
            auctionRepository.save(auction);

            broadcastAuctionUpdate(auction);
        }

        // ADM-04: Seller side: cancel SCHEDULED/ACTIVE auctions (lock auctions by id ASC)
        List<Auction> sellerAuctions = auctionRepository.findBySellerIdAndStatusIn(
                userId, List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE)
        ).stream().sorted(Comparator.comparing(Auction::getId)).toList();

        List<Long> cancelledAuctionIds = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);

        for (Auction sa : sellerAuctions) {
            Auction auc = auctionRepository.findByIdForUpdate(sa.getId()).orElse(null);
            if (auc == null) continue;
            if (auc.getStatus() != AuctionStatus.SCHEDULED && auc.getStatus() != AuctionStatus.ACTIVE) continue;

            auc.setStatus(AuctionStatus.CANCELLED);
            auc.setCancelReason("ACCOUNT_BANNED");
            auc.setClosedAt(now);

            List<Bid> bids = bidRepository.findByAuctionId(auc.getId());
            for (Bid b : bids) {
                if (b.getStatus() == BidStatus.LEADING) {
                    b.setStatus(BidStatus.NOT_WON);
                    bidRepository.save(b);
                }
            }
            auctionRepository.save(auc);
            cancelledAuctionIds.add(auc.getId());
            auditService.log(adminId, "AUCTION_CANCEL", "AUCTION", auc.getId(), "ACCOUNT_BANNED");
            broadcastAuctionUpdate(auc);
        }

        // Hide seller products in AVAILABLE / IN_AUCTION / RESERVED
        List<Product> sellerProducts = productRepository.findBySellerIdAndStatusIn(
                userId, List.of(ProductStatus.AVAILABLE, ProductStatus.IN_AUCTION, ProductStatus.RESERVED)
        );
        List<Long> hiddenProductIds = new ArrayList<>();
        for (Product prod : sellerProducts) {
            Product p = productRepository.findByIdForUpdate(prod.getId()).orElse(null);
            if (p != null) {
                p.setStatus(ProductStatus.HIDDEN);
                productRepository.save(p);
                hiddenProductIds.add(p.getId());
            }
        }

        // ADM-05: Unpaid orders (buyer or seller): cancel PENDING_PAYMENT orders (lock order by id ASC)
        List<Order> unpaidOrders = orderRepository.findUnpaidOrdersByUser(userId);
        List<Long> cancelledOrderIds = new ArrayList<>();

        for (Order uo : unpaidOrders) {
            // Lock Product (L3) -> Order (L4)
            Product prod = productRepository.findByIdForUpdate(uo.getProductId()).orElse(null);
            Order o = orderRepository.findByIdForUpdate(uo.getId()).orElse(null);
            if (o == null || o.getStatus() != OrderStatus.PENDING_PAYMENT) continue;

            o.cancel("ACCOUNT_BANNED", now);
            orderRepository.save(o);
            cancelledOrderIds.add(o.getId());

            // Cancel pending payments (L5)
            List<Payment> payments = paymentRepository.findByOrderId(o.getId());
            for (Payment pm : payments) {
                if (pm.getStatus() == PaymentStatus.PENDING) {
                    pm.setStatus(PaymentStatus.FAILED);
                    pm.setFailureReason("ORDER_CANCELLED");
                    paymentRepository.save(pm);
                }
            }

            if (prod != null && prod.getStatus() != ProductStatus.HIDDEN && prod.getStatus() != ProductStatus.REMOVED) {
                prod.setStatus(ProductStatus.AVAILABLE);
                productRepository.save(prod);
            }

            auditService.log(adminId, "ORDER_CANCEL", "ORDER", o.getId(), "ACCOUNT_BANNED");

            Long otherPartyId = o.getBuyerId().equals(userId) ? o.getSellerId() : o.getBuyerId();
            notificationService.notify(otherPartyId, NotificationType.ORDER_UPDATE,
                    NotificationTemplates.orderCancelledTitle(o.getId(), "ACCOUNT_BANNED"),
                    NotificationTemplates.orderCancelledContent(o.getId(), "ACCOUNT_BANNED"),
                    "ORDER", o.getId());
        }

        auditService.log(adminId, "USER_BAN", "USER", userId, reason);

        return Map.of(
                "invalidatedBids", invalidatedBids,
                "affectedAuctionIds", affectedAuctionIds,
                "cancelledAuctionIds", cancelledAuctionIds,
                "hiddenProductIds", hiddenProductIds,
                "cancelledOrderIds", cancelledOrderIds
        );
    }

    @Override
    @Transactional
    public Map<String, Object> banUser(Long userId, String reason, Long adminId) {
        return banUserInternal(userId, reason, adminId);
    }

    // =========================================================================
    // PRODUCT MANAGEMENT (ADM-07, 08)
    // =========================================================================

    @Override
    @Transactional
    public ProductHideResponse hideProduct(CurrentUser admin, Long productId, ProductHideRequest request) {
        requireAdmin(admin);

        Map<String, Object> effects = hideProductInternal(productId, request.reason(), admin.id());
        @SuppressWarnings("unchecked")
        List<Long> cancelledAuctionIds = (List<Long>) effects.getOrDefault("cancelledAuctionIds", List.of());
        @SuppressWarnings("unchecked")
        List<Long> cancelledOrderIds = (List<Long>) effects.getOrDefault("cancelledOrderIds", List.of());

        return new ProductHideResponse(productId, ProductStatus.HIDDEN, cancelledAuctionIds, cancelledOrderIds);
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
        Product productToInspect = productRepository.findById(productId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Product not found", HttpStatus.NOT_FOUND));

        if (productToInspect.getStatus() == ProductStatus.HIDDEN
                || productToInspect.getStatus() == ProductStatus.REMOVED
                || productToInspect.getStatus() == ProductStatus.SOLD) {
            throw new AppException("INVALID_STATUS_TRANSITION",
                    "Cannot hide product in status: " + productToInspect.getStatus(), HttpStatus.CONFLICT);
        }

        // Lock ordering: 1. Auctions (L2, id ASC) -> 2. Product (L3) -> 3. Orders (L4, id ASC)
        List<Auction> openAuctions = auctionRepository.findByProductIdAndStatusIn(
                productId, List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE)
        ).stream().sorted(Comparator.comparing(Auction::getId)).toList();

        List<Long> cancelledAuctionIds = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);

        for (Auction oa : openAuctions) {
            Auction auc = auctionRepository.findByIdForUpdate(oa.getId()).orElse(null);
            if (auc == null) continue;
            auc.setStatus(AuctionStatus.CANCELLED);
            auc.setCancelReason("PRODUCT_HIDDEN");
            auc.setClosedAt(now);

            List<Bid> bids = bidRepository.findByAuctionId(auc.getId());
            for (Bid b : bids) {
                if (b.getStatus() == BidStatus.LEADING) {
                    b.setStatus(BidStatus.NOT_WON);
                    bidRepository.save(b);
                }
            }
            auctionRepository.save(auc);
            cancelledAuctionIds.add(auc.getId());
            auditService.log(adminId, "AUCTION_CANCEL", "AUCTION", auc.getId(), "PRODUCT_HIDDEN");
            broadcastAuctionUpdate(auc);
        }

        // Lock Product (L3)
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Product not found", HttpStatus.NOT_FOUND));
        product.setStatus(ProductStatus.HIDDEN);
        productRepository.save(product);

        // Cancel unpaid orders of product (L4)
        List<Order> productOrders = orderRepository.findByProductIdAndStatusIn(
                productId, List.of(OrderStatus.PENDING_PAYMENT)
        ).stream().sorted(Comparator.comparing(Order::getId)).toList();

        List<Long> cancelledOrderIds = new ArrayList<>();
        for (Order po : productOrders) {
            Order ord = orderRepository.findByIdForUpdate(po.getId()).orElse(null);
            if (ord == null || ord.getStatus() != OrderStatus.PENDING_PAYMENT) continue;

            ord.cancel("PRODUCT_HIDDEN", now);
            orderRepository.save(ord);
            cancelledOrderIds.add(ord.getId());

            List<Payment> payments = paymentRepository.findByOrderId(ord.getId());
            for (Payment pm : payments) {
                if (pm.getStatus() == PaymentStatus.PENDING) {
                    pm.setStatus(PaymentStatus.FAILED);
                    pm.setFailureReason("ORDER_CANCELLED");
                    paymentRepository.save(pm);
                }
            }

            auditService.log(adminId, "ORDER_CANCEL", "ORDER", ord.getId(), "PRODUCT_HIDDEN");

            notificationService.notify(ord.getBuyerId(), NotificationType.ORDER_UPDATE,
                    NotificationTemplates.orderCancelledTitle(ord.getId(), "PRODUCT_HIDDEN"),
                    NotificationTemplates.orderCancelledContent(ord.getId(), "PRODUCT_HIDDEN"),
                    "ORDER", ord.getId());
        }

        auditService.log(adminId, "PRODUCT_HIDE", "PRODUCT", productId, reason);

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

    // =========================================================================
    // AUCTION MANAGEMENT (ADM-09)
    // =========================================================================

    @Override
    @Transactional
    public AuctionResponse cancelAuction(CurrentUser admin, Long auctionId, AdminCancelAuctionRequest request) {
        requireAdmin(admin);

        Auction auction = cancelAuctionInternal(auctionId, request.reason(), admin.id());
        return mapToAuctionResponse(auction);
    }

    private Auction cancelAuctionInternal(Long auctionId, String reason, Long adminId) {
        // Lock hierarchy: L2 Auction -> L3 Product
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Auction not found", HttpStatus.NOT_FOUND));

        if (auction.getStatus() != AuctionStatus.SCHEDULED && auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new AppException("INVALID_STATUS_TRANSITION",
                    "Cannot cancel auction in status: " + auction.getStatus(), HttpStatus.CONFLICT);
        }

        Product product = productRepository.findByIdForUpdate(auction.getProductId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "Product not found", HttpStatus.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        auction.setStatus(AuctionStatus.CANCELLED);
        auction.setCancelReason(reason);
        auction.setClosedAt(now);

        List<Bid> bids = bidRepository.findByAuctionId(auctionId);
        for (Bid b : bids) {
            if (b.getStatus() == BidStatus.LEADING) {
                b.setStatus(BidStatus.NOT_WON);
                bidRepository.save(b);
            }
        }
        auctionRepository.save(auction);

        if (product.getStatus() != ProductStatus.HIDDEN && product.getStatus() != ProductStatus.REMOVED) {
            product.setStatus(ProductStatus.AVAILABLE);
            productRepository.save(product);
        }

        auditService.log(adminId, "AUCTION_CANCEL", "AUCTION", auctionId, reason);

        // Notify seller
        notificationService.notify(auction.getSellerId(), NotificationType.AUCTION_ENDED,
                "Phiên đấu giá #" + auction.getId() + " đã bị hủy",
                "Phiên đấu giá đã bị Admin hủy. Lý do: " + reason,
                "AUCTION", auction.getId());

        broadcastAuctionUpdate(auction);

        return auction;
    }

    @Override
    @Transactional
    public Map<String, Object> cancelAuction(Long auctionId, String reason, Long adminId) {
        cancelAuctionInternal(auctionId, reason, adminId);
        return Map.of("auctionId", auctionId, "auctionStatus", "CANCELLED");
    }

    // =========================================================================
    // ORDER MANAGEMENT (ADM-11, 12)
    // =========================================================================

    @Override
    @Transactional
    public OrderResponse cancelOrder(CurrentUser admin, Long orderId, AdminCancelOrderRequest request) {
        requireAdmin(admin);

        Order order = cancelOrderInternal(orderId, request.reason(), admin.id());
        return mapToOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummaryResponse> getOrders(CurrentUser admin, OrderStatus status, Long buyerId, Long sellerId, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        requireAdmin(admin);

        Page<Order> page = orderRepository.findAdminOrders(status, buyerId, sellerId, from, to, pageable);

        return PageResponse.from(page, o -> {
            String buyerName = userRepository.findById(o.getBuyerId()).map(User::getFullName).orElse(null);
            String sellerName = userRepository.findById(o.getSellerId()).map(User::getFullName).orElse(null);
            String productTitle = productRepository.findById(o.getProductId()).map(Product::getTitle).orElse(null);
            String paymentStatus = paymentRepository.findByOrderIdOrderByAttemptNoDesc(o.getId())
                    .stream().findFirst().map(p -> p.getStatus().name()).orElse(null);
            String shipmentStatus = shipmentRepository.findByOrderId(o.getId())
                    .map(s -> s.getStatus().name()).orElse(null);

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
                    shipmentStatus,
                    o.getCreatedAt()
            );
        });
    }

    private Order cancelOrderInternal(Long orderId, String reason, Long adminId) {
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

    // =========================================================================
    // STATISTICS & AUDIT LOGS (ADM-14, 15)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public AdminStatisticsResponse getStatistics(CurrentUser admin, LocalDateTime from, LocalDateTime to) {
        requireAdmin(admin);

        LocalDateTime effectiveTo = to != null ? to : LocalDateTime.now(clock);
        LocalDateTime effectiveFrom = from != null ? from : effectiveTo.minusDays(30);

        // Users
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        long bannedUsers = userRepository.countByStatus(UserStatus.BANNED);
        long newUsersInRange = userRepository.countByCreatedAtBetween(effectiveFrom, effectiveTo);
        AdminStatisticsResponse.UserStats userStats = new AdminStatisticsResponse.UserStats(totalUsers, activeUsers, bannedUsers, newUsersInRange);

        // Products
        Map<String, Long> productStats = new LinkedHashMap<>();
        for (ProductStatus ps : ProductStatus.values()) {
            productStats.put(ps.name(), productRepository.countByStatus(ps));
        }

        // Auctions
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

        // Bids
        long totalBids = bidRepository.count();
        long bidsInRange = bidRepository.countByCreatedAtBetween(effectiveFrom, effectiveTo);
        long totalAuctions = scheduledAuctions + activeAuctions + endedAuctions + unsoldAuctions + cancelledAuctions;
        double avgPerAuction = totalAuctions > 0
                ? Math.round(((double) totalBids / totalAuctions) * 10.0) / 10.0
                : 0.0;
        AdminStatisticsResponse.BidStats bidStats = new AdminStatisticsResponse.BidStats(totalBids, bidsInRange, avgPerAuction);

        // Orders
        Map<String, Long> orderStats = new LinkedHashMap<>();
        for (OrderStatus os : OrderStatus.values()) {
            orderStats.put(os.name(), orderRepository.countByStatus(os));
        }

        // Revenue
        BigDecimal completedTotal = orderRepository.sumCompletedRevenue(effectiveFrom, effectiveTo);
        AdminStatisticsResponse.RevenueStats revenueStats = new AdminStatisticsResponse.RevenueStats(true, completedTotal);

        // Payments
        long successPayments = paymentRepository.countByStatus(PaymentStatus.SUCCESS);
        long failedPayments = paymentRepository.countByStatus(PaymentStatus.FAILED);
        long pendingPayments = paymentRepository.countByStatus(PaymentStatus.PENDING);
        long resolvedPaymentsTotal = successPayments + failedPayments;
        double successRate = resolvedPaymentsTotal > 0
                ? Math.round(((double) successPayments / resolvedPaymentsTotal) * 100.0) / 100.0
                : 0.0;
        AdminStatisticsResponse.PaymentStats paymentStats = new AdminStatisticsResponse.PaymentStats(
                successPayments, failedPayments, pendingPayments, successRate
        );

        // Reports
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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogs(CurrentUser admin, Long adminId, String action, String targetType, Long targetId, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        requireAdmin(admin);

        Page<AuditLog> page = auditLogRepository.findAdminAuditLogs(adminId, action, targetType, targetId, from, to, pageable);

        return PageResponse.from(page, a -> {
            String adminName = userRepository.findById(a.getAdminId()).map(User::getFullName).orElse("Admin");
            return new AuditLogResponse(
                    a.getId(),
                    a.getAdminId(),
                    adminName,
                    a.getAction(),
                    a.getTargetType(),
                    a.getTargetId(),
                    a.getDetail(),
                    a.getCreatedAt()
            );
        });
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private void requireAdmin(CurrentUser admin) {
        if (!admin.isAdmin()) {
            throw new AppException("FORBIDDEN", "Admin required", HttpStatus.FORBIDDEN);
        }
    }

    private void broadcastAuctionUpdate(Auction auction) {
        try {
            webSocketPublisher.publishAuctionUpdate(auction.getId(),
                    new AuctionWebSocketMessage("AUCTION_UPDATED", auction.getId(), mapToAuctionResponse(auction)));
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
