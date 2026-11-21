package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.admin.*;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.order.OrderResponse;
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
    private final ReportRepository reportRepository;
    private final CategoryRepository categoryRepository;
    private final NotificationService notificationService;
    private final WebSocketPublisher webSocketPublisher;
    private final Clock clock;

    // =========================================================================
    // USER MANAGEMENT
    // =========================================================================

    @Override
    @Transactional
    public UserStatusUpdateResponse updateUserStatus(CurrentUser admin, Long userId, UserStatusUpdateRequest request) {
        requireAdmin(admin);

        if (request.status() == UserStatus.BANNED) {
            return new UserStatusUpdateResponse(userId, UserStatus.BANNED, banUser(userId, request.reason(), admin.id()));
        } else if (request.status() == UserStatus.ACTIVE) {
            unbanUserInternal(userId, request.reason(), admin.id());
            return new UserStatusUpdateResponse(userId, UserStatus.ACTIVE, Map.of());
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trạng thái người dùng không hợp lệ");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getUsers(CurrentUser admin, UserStatus status, String q, Pageable pageable) {
        requireAdmin(admin);

        Page<User> page = userRepository.findAdminUsers(status, q, pageable);
        return PageResponse.from(page, u -> new AdminUserResponse(u.getId(), u.getFullName(), u.getEmail(),
                u.getPhoneNumber(), u.getAvatarUrl(), u.getRole(), u.getStatus(), u.getCreatedAt()));
    }

    private void unbanUserInternal(Long userId, String reason, Long adminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Người dùng không tồn tại"));

        if (!user.isBanned()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Tài khoản hiện không bị khóa");
        }

        user.unban();
        userRepository.save(user);

        log.info("Admin {} unbanned user {}: {}", adminId, userId, reason);
    }

    @Override
    @Transactional
    public Map<String, Object> banUser(Long userId, String reason, Long adminId) {
        if (userId.equals(adminId)) {
            throw new BusinessException(ErrorCode.CANNOT_BAN_ADMIN, "Không thể khóa tài khoản chính mình");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Người dùng không tồn tại"));

        if (user.getRole() == UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_BAN_ADMIN, "Không thể khóa tài khoản ADMIN");
        }

        if (user.isBanned()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Tài khoản đã bị khóa trước đó");
        }

        user.ban();
        userRepository.save(user);

        Instant now = Instant.now(clock);

        // Hủy các phiên mở của seller
        for (Auction auc : auctionRepository.findBySellerIdAndStatusIn(userId, List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE))) {
            auc.cancel(reason != null ? reason : "ACCOUNT_BANNED");
            auc.setClosedAt(now);
            auctionRepository.save(auc);
            broadcastAuctionUpdate(auc);
        }
        // Hủy các đơn hàng chưa thanh toán của user (buyer hoặc seller)
        for (Order o : orderRepository.findUnpaidOrdersByUser(userId)) {
            o.cancel("ACCOUNT_BANNED", now);
            orderRepository.save(o);
        }
        // Ẩn các sản phẩm của seller chưa bán
        for (Product p : productRepository.findBySellerIdAndStatusIn(userId, List.of(ProductStatus.AVAILABLE, ProductStatus.IN_AUCTION, ProductStatus.RESERVED))) {
            p.hide();
            productRepository.save(p);
        }

        log.info("Admin {} banned user {}: {}", adminId, userId, reason);
        return Map.of("userId", userId, "status", "BANNED");
    }

    // =========================================================================
    // PRODUCT MANAGEMENT
    // =========================================================================

    @Override
    @Transactional
    public ProductHideResponse hideProduct(CurrentUser admin, Long productId, ProductHideRequest request) {
        requireAdmin(admin);
        hideProduct(productId, request.reason(), admin.id());
        return new ProductHideResponse(productId, ProductStatus.HIDDEN, List.of(), List.of());
    }

    @Override
    @Transactional
    public Map<String, Object> hideProduct(Long productId, String reason, Long adminId) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        if (product.getStatus() == ProductStatus.HIDDEN) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Sản phẩm đã bị ẩn trước đó");
        }

        Instant now = Instant.now(clock);

        for (Auction auc : auctionRepository.findByProductIdAndStatusIn(productId, List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE))) {
            auc.cancel("PRODUCT_HIDDEN");
            auc.setClosedAt(now);
            auctionRepository.save(auc);
            broadcastAuctionUpdate(auc);
        }
        for (Order ord : orderRepository.findByProductIdAndStatusIn(productId, List.of(OrderStatus.PENDING_PAYMENT))) {
            ord.cancel("PRODUCT_HIDDEN", now);
            orderRepository.save(ord);
        }

        product.hide();
        productRepository.save(product);

        log.info("Admin {} hid product {}: {}", adminId, productId, reason);
        return Map.of("productId", productId, "status", "HIDDEN");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminProductResponse> getProducts(CurrentUser admin, ProductStatus status, Long sellerId, Long categoryId, Pageable pageable) {
        requireAdmin(admin);

        Page<Product> page = productRepository.findAdminProducts(status, sellerId, categoryId, pageable);
        return PageResponse.from(page, p -> {
            String sellerName = userRepository.findById(p.getSellerId()).map(User::getFullName).orElse(null);
            String categoryName = categoryRepository.findById(p.getCategoryId()).map(Category::getName).orElse(null);
            return new AdminProductResponse(p.getId(), p.getSellerId(), sellerName, p.getCategoryId(), categoryName,
                    p.getTitle(), p.getDescription(), p.getCondition(), p.getStatus(), p.getCreatedAt());
        });
    }

    // =========================================================================
    // AUCTION MANAGEMENT
    // =========================================================================

    @Override
    @Transactional
    public AuctionResponse cancelAuction(CurrentUser admin, Long auctionId, AdminCancelAuctionRequest request) {
        requireAdmin(admin);
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Lý do hủy phiên là bắt buộc");
        }
        cancelAuction(auctionId, request.reason(), admin.id());
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Phiên không tồn tại"));
        return mapToAuctionResponse(auction);
    }

    @Override
    @Transactional
    public Map<String, Object> cancelAuction(Long auctionId, String reason, Long adminId) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Phiên không tồn tại"));

        if (auction.getStatus() != AuctionStatus.SCHEDULED && auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Không thể hủy phiên ở trạng thái " + auction.getStatus());
        }

        Product product = productRepository.findByIdForUpdate(auction.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        Instant now = Instant.now(clock);
        auction.cancel(reason);
        auction.setClosedAt(now);
        auctionRepository.save(auction);

        product.release();
        productRepository.save(product);

        log.info("Admin {} cancelled auction {}: {}", adminId, auctionId, reason);
        broadcastAuctionUpdate(auction);

        return Map.of("auctionId", auctionId, "auctionStatus", "CANCELLED");
    }

    // =========================================================================
    // ORDER MANAGEMENT
    // =========================================================================

    @Override
    @Transactional
    public OrderResponse cancelOrder(CurrentUser admin, Long orderId, AdminCancelOrderRequest request) {
        requireAdmin(admin);
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Lý do hủy đơn là bắt buộc");
        }
        cancelOrder(orderId, request.reason(), admin.id());
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại"));
        return mapToOrderResponse(order);
    }

    @Override
    @Transactional
    public Map<String, Object> cancelOrder(Long orderId, String reason, Long adminId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại"));

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Chỉ có thể hủy đơn hàng ở trạng thái PENDING_PAYMENT");
        }

        Product product = productRepository.findByIdForUpdate(order.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        Instant now = Instant.now(clock);
        order.cancel(reason, now);
        orderRepository.save(order);

        product.release();
        productRepository.save(product);

        log.info("Admin {} cancelled order {}: {}", adminId, orderId, reason);
        return Map.of("orderId", orderId, "orderStatus", "CANCELLED");
    }

    @Override
    @Transactional
    public Map<String, Object> completeOrder(Long orderId, String note, Long adminId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại"));

        Instant now = Instant.now(clock);
        order.autoComplete(now);
        orderRepository.save(order);

        Product product = productRepository.findByIdForUpdate(order.getProductId()).orElse(null);
        if (product != null) {
            product.markSold();
            productRepository.save(product);
        }

        log.info("Admin {} completed order {}: note={}", adminId, orderId, note);
        return Map.of("orderStatus", "COMPLETED", "productStatus", "SOLD");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummaryResponse> getOrders(CurrentUser admin, OrderStatus status, Long buyerId, Long sellerId, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        requireAdmin(admin);

        Instant fromInstant = from != null ? from.toInstant(java.time.ZoneOffset.UTC) : null;
        Instant toInstant = to != null ? to.toInstant(java.time.ZoneOffset.UTC) : null;
        Page<Order> page = orderRepository.findAdminOrders(status, buyerId, sellerId, fromInstant, toInstant, pageable);

        return PageResponse.from(page, o -> {
            String buyerName = userRepository.findById(o.getBuyerId()).map(User::getFullName).orElse(null);
            String sellerName = userRepository.findById(o.getSellerId()).map(User::getFullName).orElse(null);
            String productTitle = productRepository.findById(o.getProductId()).map(Product::getTitle).orElse(null);
            String paymentStatus = paymentRepository.findByOrderIdOrderByAttemptNoDesc(o.getId())
                    .stream().findFirst().map(p -> p.getStatus().name()).orElse(null);
            return new AdminOrderSummaryResponse(o.getId(), o.getAuctionId(), o.getProductId(), productTitle,
                    o.getBuyerId(), buyerName, o.getSellerId(), sellerName, o.getFinalPrice(), o.getStatus(),
                    paymentStatus, o.getStatus().name(), o.getCreatedAt());
        });
    }

    // =========================================================================
    // STATISTICS
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public AdminStatisticsResponse getStatistics(CurrentUser admin, LocalDateTime from, LocalDateTime to) {
        requireAdmin(admin);
        LocalDateTime effectiveTo = to != null ? to : LocalDateTime.now(clock);
        LocalDateTime effectiveFrom = from != null ? from : effectiveTo.minusDays(30);
        Instant toInstant = effectiveTo.toInstant(java.time.ZoneOffset.UTC);
        Instant fromInstant = effectiveFrom.toInstant(java.time.ZoneOffset.UTC);

        long totalUsers = userRepository.count(), activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        long bannedUsers = userRepository.countByStatus(UserStatus.BANNED);
        long newUsers = userRepository.countByCreatedAtBetween(fromInstant, toInstant);
        var userStats = new AdminStatisticsResponse.UserStats(totalUsers, activeUsers, bannedUsers, newUsers);

        Map<String, Long> productStats = new LinkedHashMap<>();
        for (ProductStatus ps : ProductStatus.values()) productStats.put(ps.name(), productRepository.countByStatus(ps));

        long sched = auctionRepository.countByStatus(AuctionStatus.SCHEDULED);
        long active = auctionRepository.countByStatus(AuctionStatus.ACTIVE);
        long ended = auctionRepository.countByStatus(AuctionStatus.ENDED);
        long unsold = auctionRepository.countByStatus(AuctionStatus.UNSOLD);
        long cancelled = auctionRepository.countByStatus(AuctionStatus.CANCELLED);
        long closed = ended + unsold;
        double sellThrough = closed > 0 ? Math.round(((double) ended / closed) * 100.0) / 100.0 : 0.0;
        var auctionStats = new AdminStatisticsResponse.AuctionStats(sched, active, ended, unsold, cancelled, sellThrough);

        long totalBids = bidRepository.count(), bidsInRange = bidRepository.countByCreatedAtBetween(fromInstant, toInstant);
        long totalAuct = sched + active + ended + unsold + cancelled;
        double avgPerAuct = totalAuct > 0 ? Math.round(((double) totalBids / totalAuct) * 10.0) / 10.0 : 0.0;
        var bidStats = new AdminStatisticsResponse.BidStats(totalBids, bidsInRange, avgPerAuct);

        Map<String, Long> orderStats = new LinkedHashMap<>();
        for (OrderStatus os : OrderStatus.values()) orderStats.put(os.name(), orderRepository.countByStatus(os));

        BigDecimal rev = orderRepository.sumCompletedRevenue(fromInstant, toInstant);
        var revenueStats = new AdminStatisticsResponse.RevenueStats(true, rev);

        long succPay = paymentRepository.countByStatus(PaymentStatus.SUCCESS);
        long failPay = paymentRepository.countByStatus(PaymentStatus.FAILED);
        long totalPay = succPay + failPay;
        double succRate = totalPay > 0 ? Math.round(((double) succPay / totalPay) * 100.0) / 100.0 : 0.0;
        var paymentStats = new AdminStatisticsResponse.PaymentStats(succPay, failPay, 0L, succRate);

        Map<String, Long> reportStats = new LinkedHashMap<>();
        for (ReportStatus rs : ReportStatus.values()) reportStats.put(rs.name(), reportRepository.countByStatus(rs));

        return new AdminStatisticsResponse(new AdminStatisticsResponse.RangeDto(effectiveFrom, effectiveTo),
                userStats, productStats, auctionStats, bidStats, orderStats, revenueStats, paymentStats, reportStats);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private void requireAdmin(CurrentUser admin) {
        if (!admin.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Yêu cầu quyền quản trị viên");
        }
    }

    private void broadcastAuctionUpdate(Auction auction) {
        try {
            webSocketPublisher.publishAuctionUpdate(auction.getId(), mapToAuctionResponse(auction));
        } catch (Exception ex) {
            log.warn("Failed to broadcast auction update for auction {}", auction.getId(), ex);
        }
    }

    private AuctionResponse mapToAuctionResponse(Auction a) {
        return AuctionResponse.builder().id(a.getId()).productId(a.getProductId()).sellerId(a.getSellerId())
                .startPrice(a.getStartPrice()).bidIncrement(a.getBidIncrement()).currentPrice(a.getCurrentPrice())
                .highestBidderId(a.getHighestBidderId()).bidCount(a.getBidCount()).startTime(a.getStartTime())
                .endTime(a.getEndTime()).status(a.getStatus()).winnerId(a.getWinnerId()).finalPrice(a.getFinalPrice())
                .closedAt(a.getClosedAt()).cancelReason(a.getCancelReason()).createdAt(a.getCreatedAt()).build();
    }

    private OrderResponse mapToOrderResponse(Order o) {
        return OrderResponse.builder().id(o.getId()).auctionId(o.getAuctionId()).productId(o.getProductId())
                .finalPrice(o.getFinalPrice()).status(o.getStatus()).paymentDeadline(o.getPaymentDeadline())
                .createdAt(o.getCreatedAt()).build();
    }
}
