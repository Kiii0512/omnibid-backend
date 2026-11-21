package com.omnibid.service.impl;

import com.omnibid.common.*;
import com.omnibid.dto.admin.*;
import com.omnibid.dto.auction.AuctionDetailResponse;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.order.OrderDetailResponse;
import com.omnibid.dto.order.OrderResponse;
import com.omnibid.dto.payment.PaymentHistoryResponse;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.dto.product.ProductResponse;
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
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final CategoryRepository categoryRepository;
    private final AdminNotificationHelper notificationHelper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getUsers(CurrentUser admin, String keyword, UserStatus status, UserRole role, Pageable pageable) {
        requireAdmin(admin);
        Page<User> page = userRepository.findAdminUsers(status, role, keyword, pageable);
        return PageResponse.from(page, this::mapToUserResponse);
    }

    @Override
    @Transactional
    public AdminBanUserResponse banUser(CurrentUser admin, Long userId, String reason) {
        requireAdmin(admin);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Người dùng không tồn tại"));

        if (user.getRole() == UserRole.ADMIN) throw new BusinessException(ErrorCode.CANNOT_BAN_ADMIN, "Không thể khóa tài khoản ADMIN");
        if (user.isBanned()) throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Tài khoản đã bị khóa trước đó");

        user.ban();
        userRepository.save(user);
        Instant now = Instant.now(clock);
        log.info("Admin {} banned user {}: {}", admin.id(), userId, reason);

        List<Auction> sellerAuctions = auctionRepository.findBySellerIdAndStatusIn(userId, List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE))
                .stream().sorted(Comparator.comparing(Auction::getId)).toList();
        List<AdminNotificationHelper.AuctionCancelledRecord> auctionRecords = new ArrayList<>();
        for (Auction a : sellerAuctions) {
            Auction locked = auctionRepository.findByIdForUpdate(a.getId()).orElse(null);
            if (locked != null && (locked.getStatus() == AuctionStatus.SCHEDULED || locked.getStatus() == AuctionStatus.ACTIVE)) {
                locked.cancel("Tài khoản người bán bị khóa");
                locked.setClosedAt(now);
                auctionRepository.save(locked);
                auctionRecords.add(new AdminNotificationHelper.AuctionCancelledRecord(locked.getId(), locked.getSellerId(), locked.getHighestBidderId()));
            }
        }

        List<Order> unpaidOrders = orderRepository.findUnpaidOrdersByUser(userId)
                .stream().sorted(Comparator.comparing(Order::getId)).toList();
        List<AdminNotificationHelper.OrderCancelledRecord> orderRecords = new ArrayList<>();
        for (Order o : unpaidOrders) {
            Order locked = orderRepository.findByIdForUpdate(o.getId()).orElse(null);
            if (locked != null && locked.getStatus() == OrderStatus.PENDING_PAYMENT) {
                locked.cancel("Tài khoản liên quan bị khóa", now);
                orderRepository.save(locked);
                Long other = locked.getBuyerId().equals(userId) ? locked.getSellerId() : locked.getBuyerId();
                orderRecords.add(new AdminNotificationHelper.OrderCancelledRecord(locked.getId(), other));
            }
        }

        Set<Long> productIds = new HashSet<>();
        sellerAuctions.forEach(a -> productIds.add(a.getProductId()));
        unpaidOrders.forEach(o -> productIds.add(o.getProductId()));
        releaseProducts(productIds.stream().sorted().toList());

        notificationHelper.dispatchBanNotifications(auctionRecords, orderRecords, now);
        return new AdminBanUserResponse(userId, UserStatus.BANNED, auctionRecords.size(), orderRecords.size());
    }

    @Override
    @Transactional
    public AdminUserResponse unbanUser(CurrentUser admin, Long userId, String reason) {
        requireAdmin(admin);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Người dùng không tồn tại"));

        if (!user.isBanned()) throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Tài khoản hiện không bị khóa");
        user.unban();
        userRepository.save(user);
        log.info("Admin {} unbanned user {}: {}", admin.id(), userId, reason);
        return mapToUserResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminProductResponse> getProducts(CurrentUser admin, String keyword, Long categoryId, ProductStatus status, Pageable pageable) {
        requireAdmin(admin);
        Page<Product> page = productRepository.findAdminProducts(status, null, categoryId, keyword, pageable);
        return PageResponse.from(page, this::mapToAdminProductResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(CurrentUser admin, Long productId) {
        requireAdmin(admin);
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        String sellerName = getUserName(p.getSellerId());
        String catName = categoryRepository.findById(p.getCategoryId()).map(Category::getName).orElse(null);
        List<ProductImageResponse> images = productImageRepository.findByProductIdOrderByIdAsc(p.getId())
                .stream().map(img -> new ProductImageResponse(img.getId(), img.getImageUrl(), img.getIsPrimary())).toList();

        return new ProductResponse(p.getId(), p.getSellerId(), sellerName, p.getCategoryId(), catName,
                p.getTitle(), p.getDescription(), p.getCondition(), p.getStatus(), images, p.getCreatedAt(), p.getUpdatedAt());
    }

    @Override
    @Transactional
    public ProductHideResponse hideProduct(CurrentUser admin, Long productId, String reason) {
        requireAdmin(admin);
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        if (product.getStatus() == ProductStatus.HIDDEN) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Sản phẩm đã bị ẩn trước đó");
        }

        Instant now = Instant.now(clock);
        List<Auction> auctions = auctionRepository.findByProductIdAndStatusIn(productId, List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE))
                .stream().sorted(Comparator.comparing(Auction::getId)).toList();
        List<Long> cancelledAuctionIds = new ArrayList<>();
        List<AdminNotificationHelper.AuctionCancelledRecord> auctionRecords = new ArrayList<>();
        for (Auction a : auctions) {
            Auction locked = auctionRepository.findByIdForUpdate(a.getId()).orElse(null);
            if (locked != null && (locked.getStatus() == AuctionStatus.SCHEDULED || locked.getStatus() == AuctionStatus.ACTIVE)) {
                locked.cancel("Sản phẩm bị ẩn bởi quản trị viên");
                locked.setClosedAt(now);
                auctionRepository.save(locked);
                cancelledAuctionIds.add(locked.getId());
                auctionRecords.add(new AdminNotificationHelper.AuctionCancelledRecord(locked.getId(), locked.getSellerId(), locked.getHighestBidderId()));
            }
        }

        List<Order> orders = orderRepository.findByProductIdAndStatusIn(productId, List.of(OrderStatus.PENDING_PAYMENT))
                .stream().sorted(Comparator.comparing(Order::getId)).toList();
        List<Long> cancelledOrderIds = new ArrayList<>();
        List<AdminNotificationHelper.OrderBothPartiesRecord> orderRecords = new ArrayList<>();
        for (Order o : orders) {
            Order locked = orderRepository.findByIdForUpdate(o.getId()).orElse(null);
            if (locked != null && locked.getStatus() == OrderStatus.PENDING_PAYMENT) {
                locked.cancel("Sản phẩm bị ẩn bởi quản trị viên", now);
                orderRepository.save(locked);
                cancelledOrderIds.add(locked.getId());
                orderRecords.add(new AdminNotificationHelper.OrderBothPartiesRecord(locked.getId(), locked.getBuyerId(), locked.getSellerId()));
            }
        }

        product.hide();
        productRepository.save(product);
        log.info("Admin {} hid product {}: {}", admin.id(), productId, reason);

        notificationHelper.dispatchHideProductNotifications(auctionRecords, orderRecords, now);

        return new ProductHideResponse(productId, ProductStatus.HIDDEN, cancelledAuctionIds, cancelledOrderIds);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuctionResponse> getAuctions(CurrentUser admin, String keyword, AuctionStatus status, Long sellerId, Pageable pageable) {
        requireAdmin(admin);
        Page<Auction> page = auctionRepository.findAdminAuctions(status, sellerId, keyword, pageable);
        return PageResponse.from(page, this::mapToAuctionResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AuctionDetailResponse getAuctionById(CurrentUser admin, Long auctionId) {
        requireAdmin(admin);
        Auction a = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Phiên đấu giá không tồn tại"));

        Product product = productRepository.findById(a.getProductId()).orElse(null);
        String productTitle = product != null ? product.getTitle() : null;
        String productDesc = product != null ? product.getDescription() : null;
        Long catId = product != null ? product.getCategoryId() : null;
        String catName = catId != null ? categoryRepository.findById(catId).map(Category::getName).orElse(null) : null;
        ProductCondition condition = product != null ? product.getCondition() : null;

        String sellerName = getUserName(a.getSellerId());
        String bidderName = a.getHighestBidderId() != null ? getUserName(a.getHighestBidderId()) : null;
        List<ProductImageResponse> images = productImageRepository.findByProductIdOrderByIdAsc(a.getProductId())
                .stream().map(img -> new ProductImageResponse(img.getId(), img.getImageUrl(), img.getIsPrimary())).toList();
        String primaryImage = images.stream().filter(ProductImageResponse::isPrimary).findFirst().map(ProductImageResponse::imageUrl).orElse(null);

        AuctionDetailResponse detail = AuctionDetailResponse.builder()
                .id(a.getId()).productId(a.getProductId()).title(productTitle).description(productDesc)
                .categoryId(catId).categoryName(catName).condition(condition).sellerId(a.getSellerId()).sellerName(sellerName)
                .startPrice(a.getStartPrice()).bidIncrement(a.getBidIncrement()).currentPrice(a.getCurrentPrice()).minNextBid(a.minNextBid())
                .reservePrice(a.getReservePrice()).reserveStatus(a.reserveStatus()).highestBidderName(bidderName).bidCount(a.getBidCount())
                .startTime(a.getStartTime()).endTime(a.getEndTime()).status(a.getStatus()).winnerId(a.getWinnerId()).finalPrice(a.getFinalPrice())
                .closedAt(a.getClosedAt()).cancelReason(a.getCancelReason()).primaryImageUrl(primaryImage).images(images).createdAt(a.getCreatedAt()).build();
        return detail;
    }

    @Override
    @Transactional
    public AuctionResponse cancelAuction(CurrentUser admin, Long auctionId, String reason) {
        requireAdmin(admin);
        if (reason == null || reason.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Lý do hủy phiên là bắt buộc");

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
        log.info("Admin {} cancelled auction {}: {}", admin.id(), auctionId, reason);
        notificationHelper.notifyAuctionCancelled(auction.getId(), auction.getSellerId(), auction.getHighestBidderId(), reason, now);
        return mapToAuctionResponse(auction);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummaryResponse> getOrders(CurrentUser admin, OrderStatus status, Long buyerId, Long sellerId, Instant from, Instant to, Pageable pageable) {
        requireAdmin(admin);
        Page<Order> page = orderRepository.findAdminOrders(status, buyerId, sellerId, from, to, pageable);
        return PageResponse.from(page, this::mapToAdminOrderSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOrderDetailResponse getOrderById(CurrentUser admin, Long orderId) {
        requireAdmin(admin);
        Order o = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại"));

        String sellerName = getUserName(o.getSellerId());
        String buyerName = getUserName(o.getBuyerId());
        String productTitle = productRepository.findById(o.getProductId()).map(Product::getTitle).orElse(null);
        String productImageUrl = productImageRepository.findByProductIdAndIsPrimaryTrue(o.getProductId()).map(ProductImage::getImageUrl).orElse(null);

        OrderDetailResponse orderDto = OrderDetailResponse.builder()
                .id(o.getId()).auctionId(o.getAuctionId()).productId(o.getProductId())
                .productTitle(productTitle).productImageUrl(productImageUrl).sellerId(o.getSellerId()).sellerName(sellerName)
                .buyerId(o.getBuyerId()).buyerName(buyerName).finalPrice(o.getFinalPrice()).status(o.getStatus())
                .shippingAddress(o.getShippingAddress()).phoneNumber(o.getPhoneNumber()).trackingCode(o.getTrackingCode())
                .paymentDeadline(o.getPaymentDeadline()).paidAt(o.getPaidAt()).shippedAt(o.getShippedAt()).confirmDeadline(o.getConfirmDeadline())
                .completedAt(o.getCompletedAt()).cancelledAt(o.getCancelledAt()).cancelReason(o.getCancelReason())
                .viewerRole("ADMIN").allowedActions(List.of()).build();

        List<PaymentHistoryResponse> payments = paymentRepository.findByOrderIdOrderByAttemptNoDesc(orderId).stream()
                .map(p -> PaymentHistoryResponse.builder().paymentId(p.getId()).attemptNo(p.getAttemptNo()).method(p.getMethod())
                        .amount(p.getAmount()).status(p.getStatus()).failureReason(p.getFailureReason()).createdAt(p.getCreatedAt()).build()).toList();

        return new AdminOrderDetailResponse(orderDto, payments);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(CurrentUser admin, Long orderId, String reason) {
        requireAdmin(admin);
        if (reason == null || reason.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Lý do hủy đơn là bắt buộc");

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
        log.info("Admin {} cancelled order {}: {}", admin.id(), orderId, reason);

        notificationHelper.notifyOrderCancelled(order.getId(), order.getBuyerId(), reason);
        notificationHelper.notifyOrderCancelled(order.getId(), order.getSellerId(), reason);

        return mapToOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStatisticsResponse getStatistics(CurrentUser admin) {
        requireAdmin(admin);
        Map<AuctionStatus, Long> auctions = new LinkedHashMap<>();
        for (AuctionStatus s : AuctionStatus.values()) auctions.put(s, auctionRepository.countByStatus(s));
        Map<OrderStatus, Long> orders = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) orders.put(s, orderRepository.countByStatus(s));
        BigDecimal rev = orderRepository.sumTotalCompletedRevenue();
        return new AdminStatisticsResponse(userRepository.count(), auctions, orders, rev != null ? rev : BigDecimal.ZERO, reportRepository.countByStatus(ReportStatus.PENDING));
    }

    private void requireAdmin(CurrentUser admin) {
        if (!admin.isAdmin()) throw new BusinessException(ErrorCode.FORBIDDEN, "Yêu cầu quyền quản trị viên");
    }

    private String getUserName(Long uid) {
        return uid != null ? userRepository.findById(uid).map(User::getFullName).orElse(null) : null;
    }

    private void releaseProducts(List<Long> productIds) {
        for (Long pid : productIds) {
            Product product = productRepository.findByIdForUpdate(pid).orElse(null);
            if (product != null && product.getStatus() != ProductStatus.HIDDEN) {
                product.release();
                productRepository.save(product);
            }
        }
    }

    private AdminUserResponse mapToUserResponse(User u) {
        return new AdminUserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhoneNumber(), u.getAvatarUrl(), u.getRole(), u.getStatus(), u.getCreatedAt());
    }

    private AdminProductResponse mapToAdminProductResponse(Product p) {
        String sName = getUserName(p.getSellerId());
        String cName = categoryRepository.findById(p.getCategoryId()).map(Category::getName).orElse(null);
        return new AdminProductResponse(p.getId(), p.getSellerId(), sName, p.getCategoryId(), cName, p.getTitle(), p.getDescription(), p.getCondition(), p.getStatus(), p.getCreatedAt());
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
                .finalPrice(o.getFinalPrice()).status(o.getStatus()).paymentDeadline(o.getPaymentDeadline()).createdAt(o.getCreatedAt()).build();
    }

    private AdminOrderSummaryResponse mapToAdminOrderSummaryResponse(Order o) {
        String bName = getUserName(o.getBuyerId());
        String sName = getUserName(o.getSellerId());
        String pTitle = productRepository.findById(o.getProductId()).map(Product::getTitle).orElse(null);
        String pStatus = paymentRepository.findByOrderIdOrderByAttemptNoDesc(o.getId()).stream().findFirst().map(p -> p.getStatus().name()).orElse(null);
        return new AdminOrderSummaryResponse(o.getId(), o.getAuctionId(), o.getProductId(), pTitle, o.getBuyerId(), bName, o.getSellerId(), sName, o.getFinalPrice(), o.getStatus(), pStatus, o.getStatus().name(), o.getCreatedAt());
    }
}
