package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.MaskingUtils;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.common.PageResponse;
import com.omnibid.common.SortWhitelist;
import com.omnibid.dto.admin.*;
import com.omnibid.dto.order.OrderSummaryResponse;
import com.omnibid.dto.payment.PaymentHistoryResponse;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.dto.user.UserRef;
import com.omnibid.dto.user.UserSummary;
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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private static final List<AuctionStatus> OPEN_AUCTION_STATUSES =
            List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE);

    private static final Set<String> USER_SORT_FIELDS = Set.of("createdAt", "id", "fullName", "email");
    private static final Set<String> PRODUCT_SORT_FIELDS = Set.of("createdAt", "id", "title");
    private static final Set<String> AUCTION_SORT_FIELDS = Set.of("createdAt", "id", "startTime", "endTime", "currentPrice");
    private static final Set<String> ORDER_SORT_FIELDS = Set.of("createdAt", "id", "finalPrice", "paymentDeadline");

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final CategoryRepository categoryRepository;
    private final NotificationService notificationService;
    private final WebSocketPublisher webSocketPublisher;
    private final Clock clock;

    // -------------------------------------------------------------------------
    // Users
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getUsers(CurrentUser admin, String keyword, UserStatus status, UserRole role, Pageable pageable) {
        requireAdmin(admin);
        SortWhitelist.validate(pageable, USER_SORT_FIELDS);

        Page<User> page = userRepository.findAdminUsers(normalizeKeyword(keyword), status, role, pageable);
        return PageResponse.from(page, u -> new AdminUserResponse(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getRole(),
                u.getStatus(),
                u.getAvatarUrl(),
                u.getPhoneNumber(),
                u.getCreatedAt()
        ));
    }

    @Override
    @Transactional
    public AdminBanUserResponse banUser(CurrentUser admin, Long userId, String reason) {
        requireAdmin(admin);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng"));
        if (user.getRole() == UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_BAN_ADMIN, "Không thể khóa tài khoản quản trị viên");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Tài khoản đã bị khóa");
        }

        // Single UPDATE without pessimistic lock on users (BUSINESS_RULES §5.9)
        userRepository.updateStatus(userId, UserStatus.BANNED);

        Instant now = Instant.now(clock);
        String auctionCancelReason = "Tài khoản người bán bị khóa";

        // Round 1: Lock open auctions where user is seller (id asc)
        List<Long> sellerAuctionIds = auctionRepository.findIdsBySellerIdAndStatusInOrderByIdAsc(userId, OPEN_AUCTION_STATUSES);
        List<Auction> cancelledAuctions = new ArrayList<>();
        SortedSet<Long> productIdsToRelease = new TreeSet<>();

        if (!sellerAuctionIds.isEmpty()) {
            for (Auction auc : auctionRepository.findAllByIdInForUpdateOrderByIdAsc(sellerAuctionIds)) {
                if (auc.isOpen()) {
                    auc.cancel(auctionCancelReason);
                    auc.setClosedAt(now);
                    auctionRepository.save(auc);
                    cancelledAuctions.add(auc);
                    productIdsToRelease.add(auc.getProductId());
                }
            }
        }

        // Round 2: Lock PENDING_PAYMENT orders where user is buyer or seller (id asc)
        List<Long> unpaidOrderIds = orderRepository.findUnpaidOrderIdsByUserOrderByIdAsc(userId);
        List<CancelledOrderNotice> cancelledOrders = new ArrayList<>();

        if (!unpaidOrderIds.isEmpty()) {
            for (Order o : orderRepository.findAllByIdInForUpdateOrderByIdAsc(unpaidOrderIds)) {
                if (o.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    boolean userIsBuyer = o.getBuyerId().equals(userId);
                    String orderCancelReason = userIsBuyer ? "Tài khoản người mua bị khóa" : "Tài khoản người bán bị khóa";
                    o.cancel(orderCancelReason, now);
                    orderRepository.save(o);
                    Long otherPartyId = userIsBuyer ? o.getSellerId() : o.getBuyerId();
                    cancelledOrders.add(new CancelledOrderNotice(o.getId(), otherPartyId, orderCancelReason));
                    if (o.getProductId() != null) {
                        productIdsToRelease.add(o.getProductId());
                    }
                }
            }
        }

        // Round 3: Lock related products (id asc) and release (except HIDDEN)
        if (!productIdsToRelease.isEmpty()) {
            for (Product prod : productRepository.findAllByIdInForUpdateOrderByIdAsc(new ArrayList<>(productIdsToRelease))) {
                if (prod.getStatus() == ProductStatus.IN_AUCTION || prod.getStatus() == ProductStatus.RESERVED) {
                    prod.release();
                    productRepository.save(prod);
                }
            }
        }

        // Notifications & WebSocket (all emitted AFTER_COMMIT)
        for (Auction auc : cancelledAuctions) {
            notifyAuctionCancelled(auc, auctionCancelReason);
            broadcastAuctionCancelled(auc, now);
        }
        for (CancelledOrderNotice notice : cancelledOrders) {
            notificationService.notify(
                    notice.recipientId(),
                    NotificationType.ORDER_UPDATE,
                    NotificationTemplates.orderCancelledTitle(notice.orderId(), notice.reason()),
                    NotificationTemplates.orderCancelledContent(notice.orderId(), notice.reason()),
                    "ORDER",
                    notice.orderId()
            );
        }

        log.info("Admin {} action=USER_BAN target=USER:{} reason={} cancelledAuctions={} cancelledOrders={}",
                admin.id(), userId, reason, cancelledAuctions.size(), cancelledOrders.size());

        return new AdminBanUserResponse(userId, UserStatus.BANNED, cancelledAuctions.size(), cancelledOrders.size());
    }

    @Override
    @Transactional
    public AdminUnbanUserResponse unbanUser(CurrentUser admin, Long userId, String reason) {
        requireAdmin(admin);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng"));
        if (user.getStatus() != UserStatus.BANNED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Tài khoản không ở trạng thái bị khóa");
        }

        userRepository.updateStatus(userId, UserStatus.ACTIVE);
        log.info("Admin {} action=USER_UNBAN target=USER:{} reason={}", admin.id(), userId, reason);

        return new AdminUnbanUserResponse(userId, UserStatus.ACTIVE);
    }

    // -------------------------------------------------------------------------
    // Products
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminProductItemResponse> getProducts(CurrentUser admin, String keyword, Long categoryId, ProductStatus status, Pageable pageable) {
        requireAdmin(admin);
        SortWhitelist.validate(pageable, PRODUCT_SORT_FIELDS);

        Page<Product> page = productRepository.findAdminProducts(normalizeKeyword(keyword), categoryId, status, pageable);
        List<Product> products = page.getContent();
        if (products.isEmpty()) {
            return PageResponse.from(page, p -> null);
        }

        List<Long> productIds = products.stream().map(Product::getId).toList();
        Set<Long> categoryIds = products.stream().map(Product::getCategoryId).collect(Collectors.toSet());
        Set<Long> sellerIds = products.stream().map(Product::getSellerId).collect(Collectors.toSet());

        Map<Long, String> categoryNames = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        Map<Long, User> sellers = userRepository.findAllById(sellerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, List<ProductImage>> imagesByProduct = productImageRepository.findByProductIdInOrderByIdAsc(productIds).stream()
                .collect(Collectors.groupingBy(ProductImage::getProductId));
        Map<Long, Long> activeAuctionByProduct = auctionRepository.findByProductIdInAndStatusIn(productIds, OPEN_AUCTION_STATUSES).stream()
                .collect(Collectors.toMap(Auction::getProductId, Auction::getId, (a, b) -> a));
        Set<Long> everAuctionedIds = auctionRepository.findProductIdsWithAnyAuction(productIds);

        return PageResponse.from(page, p -> {
            List<ProductImage> images = imagesByProduct.getOrDefault(p.getId(), List.of());
            User seller = sellers.get(p.getSellerId());
            return new AdminProductItemResponse(
                    p.getId(),
                    p.getTitle(),
                    p.getCategoryId(),
                    categoryNames.get(p.getCategoryId()),
                    p.getCondition(),
                    p.getStatus(),
                    resolvePrimaryImageUrl(images),
                    images.size(),
                    activeAuctionByProduct.get(p.getId()),
                    everAuctionedIds.contains(p.getId()),
                    p.getCreatedAt(),
                    toUserRef(seller, p.getSellerId())
            );
        });
    }

    @Override
    @Transactional(readOnly = true)
    public AdminProductDetailResponse getProductById(CurrentUser admin, Long productId) {
        requireAdmin(admin);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm"));
        String categoryName = categoryRepository.findById(product.getCategoryId())
                .map(Category::getName)
                .orElse(null);
        User seller = userRepository.findById(product.getSellerId()).orElse(null);
        List<ProductImage> images = productImageRepository.findByProductIdOrderByIdAsc(productId);
        Long activeAuctionId = auctionRepository.findFirstByProductIdAndStatusIn(productId, OPEN_AUCTION_STATUSES)
                .map(Auction::getId)
                .orElse(null);
        boolean everAuctioned = auctionRepository.existsByProductId(productId);

        List<ProductImageResponse> imageDtos = images.stream()
                .map(img -> new ProductImageResponse(img.getId(), img.getImageUrl(), Boolean.TRUE.equals(img.getIsPrimary())))
                .toList();

        return new AdminProductDetailResponse(
                product.getId(),
                product.getTitle(),
                product.getDescription(),
                product.getCategoryId(),
                categoryName,
                product.getCondition(),
                product.getStatus(),
                resolvePrimaryImageUrl(images),
                images.size(),
                activeAuctionId,
                everAuctioned,
                imageDtos,
                product.getCreatedAt(),
                product.getUpdatedAt(),
                toUserSummary(seller)
        );
    }

    @Override
    @Transactional
    public ProductHideResponse hideProduct(CurrentUser admin, Long productId, String reason) {
        requireAdmin(admin);

        // Lock order: auctions -> orders -> products (BUSINESS_RULES §5.8)
        Instant now = Instant.now(clock);
        String hideReason = "Sản phẩm bị ẩn";

        // Round 1: Lock and cancel open auctions first
        List<Long> openAuctionIds = auctionRepository.findIdsByProductIdAndStatusInOrderByIdAsc(productId, OPEN_AUCTION_STATUSES);
        List<Auction> cancelledAuctions = new ArrayList<>();
        if (!openAuctionIds.isEmpty()) {
            for (Auction auc : auctionRepository.findAllByIdInForUpdateOrderByIdAsc(openAuctionIds)) {
                if (auc.isOpen()) {
                    auc.cancel(hideReason);
                    auc.setClosedAt(now);
                    auctionRepository.save(auc);
                    cancelledAuctions.add(auc);
                }
            }
            auctionRepository.flush();
        }

        // Round 2: Query pending orders AFTER open auctions are locked/cancelled (prevents missing newly closed auction orders)
        List<Long> pendingOrderIds = orderRepository.findIdsByProductIdAndStatusInOrderByIdAsc(productId, List.of(OrderStatus.PENDING_PAYMENT));
        List<Order> cancelledOrders = new ArrayList<>();
        if (!pendingOrderIds.isEmpty()) {
            for (Order ord : orderRepository.findAllByIdInForUpdateOrderByIdAsc(pendingOrderIds)) {
                if (ord.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    ord.cancel(hideReason, now);
                    orderRepository.save(ord);
                    cancelledOrders.add(ord);
                }
            }
            orderRepository.flush();
        }

        // Round 3: Lock product and verify no concurrent auction/order was created before locking product (TOCTOU guard)
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm"));

        boolean hasConcurrentOpenAuction = auctionRepository.existsByProductIdAndStatusIn(productId, OPEN_AUCTION_STATUSES);
        boolean hasConcurrentPendingOrder = orderRepository.existsByProductIdAndStatusIn(productId, List.of(OrderStatus.PENDING_PAYMENT));
        if (hasConcurrentOpenAuction || hasConcurrentPendingOrder) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Trạng thái phiên đấu giá hoặc đơn hàng vừa thay đổi đồng thời, vui lòng thử lại"
            );
        }

        product.hide();
        productRepository.save(product);

        // Notifications & WebSocket (emitted AFTER_COMMIT; rolled back if product.hide() threw)
        for (Auction auc : cancelledAuctions) {
            notifyAuctionCancelled(auc, hideReason);
            broadcastAuctionCancelled(auc, now);
        }
        for (Order ord : cancelledOrders) {
            notifyOrderCancelledBoth(ord, hideReason);
        }

        log.info("Admin {} action=PRODUCT_HIDE target=PRODUCT:{} reason={} cancelledAuctions={} cancelledOrders={}",
                admin.id(), productId, reason, cancelledAuctions.size(), cancelledOrders.size());

        return new ProductHideResponse(productId, ProductStatus.HIDDEN, cancelledAuctions.size(), cancelledOrders.size());
    }

    // -------------------------------------------------------------------------
    // Auctions
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminAuctionItemResponse> getAuctions(CurrentUser admin, String keyword, AuctionStatus status, Long sellerId, Pageable pageable) {
        requireAdmin(admin);
        SortWhitelist.validate(pageable, AUCTION_SORT_FIELDS);

        Page<Auction> page = auctionRepository.findAdminAuctions(normalizeKeyword(keyword), status, sellerId, pageable);
        List<Auction> auctions = page.getContent();
        if (auctions.isEmpty()) {
            return PageResponse.from(page, a -> null);
        }

        Set<Long> productIds = auctions.stream().map(Auction::getProductId).collect(Collectors.toSet());
        Set<Long> sellerIds = auctions.stream().map(Auction::getSellerId).collect(Collectors.toSet());

        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Set<Long> categoryIds = products.values().stream().map(Product::getCategoryId).collect(Collectors.toSet());
        Map<Long, String> categoryNames = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        Map<Long, User> sellers = userRepository.findAllById(sellerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, List<ProductImage>> imagesByProduct = productImageRepository.findByProductIdInOrderByIdAsc(productIds).stream()
                .collect(Collectors.groupingBy(ProductImage::getProductId));

        return PageResponse.from(page, a -> {
            Product product = products.get(a.getProductId());
            Long catId = product != null ? product.getCategoryId() : null;
            List<ProductImage> images = imagesByProduct.getOrDefault(a.getProductId(), List.of());
            return new AdminAuctionItemResponse(
                    a.getId(),
                    a.getProductId(),
                    product != null ? product.getTitle() : null,
                    resolvePrimaryImageUrl(images),
                    catId,
                    catId != null ? categoryNames.get(catId) : null,
                    a.getStatus(),
                    a.getStartPrice(),
                    a.getCurrentPrice(),
                    a.minNextBid(),
                    a.getBidCount() != null ? a.getBidCount() : 0,
                    a.reserveStatus(),
                    a.getStartTime(),
                    a.getEndTime(),
                    a.getCreatedAt(),
                    toUserRef(sellers.get(a.getSellerId()), a.getSellerId()),
                    a.getReservePrice()
            );
        });
    }

    @Override
    @Transactional(readOnly = true)
    public AdminAuctionDetailResponse getAuctionById(CurrentUser admin, Long auctionId) {
        requireAdmin(admin);

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên đấu giá"));
        return buildAdminAuctionDetail(auction);
    }

    @Override
    @Transactional
    public AdminAuctionDetailResponse cancelAuction(CurrentUser admin, Long auctionId, AdminCancelAuctionRequest request) {
        requireAdmin(admin);

        // Lock order: auctions -> products
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên đấu giá"));

        String reason = request.reason().trim();
        Instant now = Instant.now(clock);
        auction.cancel(reason);
        auction.setClosedAt(now);
        auctionRepository.save(auction);

        Product product = productRepository.findByIdForUpdate(auction.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm"));
        if (product.getStatus() == ProductStatus.IN_AUCTION || product.getStatus() == ProductStatus.RESERVED) {
            product.release();
            productRepository.save(product);
        }

        notifyAuctionCancelled(auction, reason);
        broadcastAuctionCancelled(auction, now);

        log.info("Admin {} action=AUCTION_CANCEL target=AUCTION:{} reason={}", admin.id(), auctionId, reason);
        return buildAdminAuctionDetail(auction);
    }

    // -------------------------------------------------------------------------
    // Orders
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getOrders(CurrentUser admin, OrderStatus status, Long buyerId, Long sellerId, Instant fromDate, Instant toDate, Pageable pageable) {
        requireAdmin(admin);
        SortWhitelist.validate(pageable, ORDER_SORT_FIELDS);

        Page<Order> page = orderRepository.findAdminOrders(status, buyerId, sellerId, fromDate, toDate, pageable);
        List<Order> orders = page.getContent();
        if (orders.isEmpty()) {
            return PageResponse.from(page, o -> null);
        }

        Set<Long> productIds = orders.stream().map(Order::getProductId).collect(Collectors.toSet());
        Set<Long> userIds = new HashSet<>();
        for (Order o : orders) {
            userIds.add(o.getBuyerId());
            userIds.add(o.getSellerId());
        }

        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, List<ProductImage>> imagesByProduct = productImageRepository.findByProductIdInOrderByIdAsc(productIds).stream()
                .collect(Collectors.groupingBy(ProductImage::getProductId));

        return PageResponse.from(page, o -> {
            Product product = products.get(o.getProductId());
            List<ProductImage> images = imagesByProduct.getOrDefault(o.getProductId(), List.of());
            return OrderSummaryResponse.builder()
                    .id(o.getId())
                    .auctionId(o.getAuctionId())
                    .productId(o.getProductId())
                    .title(product != null ? product.getTitle() : null)
                    .primaryImageUrl(resolvePrimaryImageUrl(images))
                    .finalPrice(o.getFinalPrice())
                    .status(o.getStatus())
                    .buyer(toUserRef(users.get(o.getBuyerId()), o.getBuyerId()))
                    .seller(toUserRef(users.get(o.getSellerId()), o.getSellerId()))
                    .paymentDeadline(o.getPaymentDeadline())
                    .confirmDeadline(o.getConfirmDeadline())
                    .createdAt(o.getCreatedAt())
                    .build();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOrderDetailResponse getOrderById(CurrentUser admin, Long orderId) {
        requireAdmin(admin);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng"));
        return buildAdminOrderDetail(order);
    }

    @Override
    @Transactional
    public AdminOrderDetailResponse cancelOrder(CurrentUser admin, Long orderId, AdminCancelOrderRequest request) {
        requireAdmin(admin);

        // Lock order: orders -> products
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng"));

        String reason = request.reason().trim();
        Instant now = Instant.now(clock);
        order.cancel(reason, now);
        orderRepository.save(order);

        Product product = productRepository.findByIdForUpdate(order.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm"));
        if (product.getStatus() == ProductStatus.IN_AUCTION || product.getStatus() == ProductStatus.RESERVED) {
            product.release();
            productRepository.save(product);
        }

        notifyOrderCancelledBoth(order, reason);

        log.info("Admin {} action=ORDER_CANCEL target=ORDER:{} reason={}", admin.id(), orderId, reason);
        return buildAdminOrderDetail(order);
    }

    // -------------------------------------------------------------------------
    // Statistics
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public AdminStatisticsResponse getStatistics(CurrentUser admin) {
        requireAdmin(admin);

        long totalUsers = userRepository.count();
        long bannedUsers = userRepository.countByStatus(UserStatus.BANNED);

        Map<AuctionStatus, Long> auctionsByStatus = new LinkedHashMap<>();
        for (AuctionStatus st : AuctionStatus.values()) {
            auctionsByStatus.put(st, 0L);
        }
        for (Object[] row : auctionRepository.countGroupedByStatus()) {
            auctionsByStatus.put((AuctionStatus) row[0], ((Number) row[1]).longValue());
        }

        Map<OrderStatus, Long> ordersByStatus = new LinkedHashMap<>();
        for (OrderStatus st : OrderStatus.values()) {
            ordersByStatus.put(st, 0L);
        }
        for (Object[] row : orderRepository.countGroupedByStatus()) {
            ordersByStatus.put((OrderStatus) row[0], ((Number) row[1]).longValue());
        }

        BigDecimal completedValue = orderRepository.sumCompletedOrderValue();
        BigDecimal completedOrderValue = completedValue != null
                ? completedValue.setScale(0, RoundingMode.UNNECESSARY)
                : BigDecimal.ZERO;

        long pendingReports = reportRepository.countByStatus(ReportStatus.PENDING);

        return new AdminStatisticsResponse(
                totalUsers,
                bannedUsers,
                auctionsByStatus,
                ordersByStatus,
                completedOrderValue,
                pendingReports
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void requireAdmin(CurrentUser admin) {
        if (admin == null || !admin.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Yêu cầu quyền quản trị viên");
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private String resolvePrimaryImageUrl(List<ProductImage> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .findFirst()
                .orElse(images.get(0))
                .getImageUrl();
    }

    private UserRef toUserRef(User user, Long fallbackId) {
        if (user == null) {
            return new UserRef(fallbackId, null);
        }
        return new UserRef(user.getId(), user.getFullName());
    }

    private UserSummary toUserSummary(User user) {
        if (user == null) {
            return null;
        }
        return new UserSummary(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getAvatarUrl()
        );
    }

    private AdminAuctionDetailResponse buildAdminAuctionDetail(Auction auction) {
        Product product = productRepository.findById(auction.getProductId()).orElse(null);
        String categoryName = product != null
                ? categoryRepository.findById(product.getCategoryId()).map(Category::getName).orElse(null)
                : null;
        List<ProductImage> images = productImageRepository.findByProductIdOrderByIdAsc(auction.getProductId());
        List<ProductImageResponse> imageDtos = images.stream()
                .map(img -> new ProductImageResponse(img.getId(), img.getImageUrl(), Boolean.TRUE.equals(img.getIsPrimary())))
                .toList();

        User seller = userRepository.findById(auction.getSellerId()).orElse(null);
        User highestBidder = auction.getHighestBidderId() != null
                ? userRepository.findById(auction.getHighestBidderId()).orElse(null)
                : null;
        User winner = auction.getWinnerId() != null
                ? userRepository.findById(auction.getWinnerId()).orElse(null)
                : null;
        Long orderId = orderRepository.findByAuctionId(auction.getId()).map(Order::getId).orElse(null);

        return new AdminAuctionDetailResponse(
                auction.getId(),
                auction.getProductId(),
                product != null ? product.getTitle() : null,
                resolvePrimaryImageUrl(images),
                product != null ? product.getCategoryId() : null,
                categoryName,
                auction.getStatus(),
                auction.getStartPrice(),
                auction.getCurrentPrice(),
                auction.minNextBid(),
                auction.getBidCount() != null ? auction.getBidCount() : 0,
                auction.reserveStatus(),
                auction.getStartTime(),
                auction.getEndTime(),
                auction.getCreatedAt(),
                product != null ? product.getDescription() : null,
                product != null ? product.getCondition() : null,
                imageDtos,
                auction.getBidIncrement(),
                auction.getReservePrice(),
                toUserSummary(seller),
                toUserSummary(highestBidder),
                toUserSummary(winner),
                auction.getFinalPrice(),
                auction.getClosedAt(),
                auction.getCancelReason(),
                product != null ? product.getStatus() : null,
                orderId
        );
    }

    private AdminOrderDetailResponse buildAdminOrderDetail(Order order) {
        Product product = productRepository.findById(order.getProductId()).orElse(null);
        List<ProductImage> images = productImageRepository.findByProductIdOrderByIdAsc(order.getProductId());
        User buyer = userRepository.findById(order.getBuyerId()).orElse(null);
        User seller = userRepository.findById(order.getSellerId()).orElse(null);
        List<PaymentHistoryResponse> payments = paymentRepository.findByOrderIdOrderByAttemptNoDesc(order.getId()).stream()
                .map(p -> PaymentHistoryResponse.builder()
                        .id(p.getId())
                        .attemptNo(p.getAttemptNo())
                        .amount(p.getAmount())
                        .method(p.getMethod())
                        .status(p.getStatus())
                        .failureReason(p.getFailureReason())
                        .createdAt(p.getCreatedAt())
                        .paidAt(p.getPaidAt())
                        .build())
                .toList();

        return new AdminOrderDetailResponse(
                order.getId(),
                order.getAuctionId(),
                order.getProductId(),
                product != null ? product.getTitle() : null,
                resolvePrimaryImageUrl(images),
                order.getFinalPrice(),
                order.getStatus(),
                toUserSummary(buyer),
                toUserSummary(seller),
                order.getPaymentDeadline(),
                order.getConfirmDeadline(),
                order.getCreatedAt(),
                order.getShippingAddress(),
                order.getPhoneNumber(),
                order.getTrackingCode(),
                order.getPaidAt(),
                order.getShippedAt(),
                order.getCompletedAt(),
                order.getCancelledAt(),
                order.getCancelReason(),
                product != null ? product.getStatus() : null,
                payments
        );
    }

    private void notifyAuctionCancelled(Auction auction, String reason) {
        String title = NotificationTemplates.auctionCancelledTitle(reason);
        String content = NotificationTemplates.auctionCancelledContent(auction.getId(), reason);
        notificationService.notify(auction.getSellerId(), NotificationType.AUCTION_CANCELLED, title, content, "AUCTION", auction.getId());
        if (auction.getHighestBidderId() != null) {
            notificationService.notify(auction.getHighestBidderId(), NotificationType.AUCTION_CANCELLED, title, content, "AUCTION", auction.getId());
        }
    }

    private void notifyOrderCancelledBoth(Order order, String reason) {
        String title = NotificationTemplates.orderCancelledTitle(order.getId(), reason);
        String content = NotificationTemplates.orderCancelledContent(order.getId(), reason);
        notificationService.notify(order.getBuyerId(), NotificationType.ORDER_UPDATE, title, content, "ORDER", order.getId());
        notificationService.notify(order.getSellerId(), NotificationType.ORDER_UPDATE, title, content, "ORDER", order.getId());
    }

    private void broadcastAuctionCancelled(Auction auction, Instant now) {
        String maskedLeaderName = auction.getHighestBidderId() != null
                ? userRepository.findById(auction.getHighestBidderId())
                        .map(User::getFullName)
                        .map(MaskingUtils::maskName)
                        .orElse(null)
                : null;
        webSocketPublisher.publishAuctionEvent(
                AuctionEvent.of(AuctionEventType.AUCTION_CANCELLED, auction, maskedLeaderName, null, now)
        );
    }

    private record CancelledOrderNotice(Long orderId, Long recipientId, String reason) {}
}
