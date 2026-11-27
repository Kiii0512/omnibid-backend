package com.omnibid.service;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.dto.admin.*;
import com.omnibid.dto.websocket.AuctionEvent;
import com.omnibid.entity.*;
import com.omnibid.enums.*;
import com.omnibid.repository.*;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.impl.AdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Long ADMIN_ID = 9L;
    private static final Long USER_ID = 3L;
    private static final Long BIDDER_ID = 4L;
    private static final Long SELLER_ID = 1L;
    private static final Long AUCTION_ID = 12L;
    private static final Long ORDER_ID = 20L;
    private static final Long PRODUCT_ID = 10L;

    private final CurrentUser adminUser = new CurrentUser(ADMIN_ID, true);
    private final CurrentUser regularUser = new CurrentUser(USER_ID, false);

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private WebSocketPublisher webSocketPublisher;

    private AdminServiceImpl adminService;

    private User activeUser;
    private Product product;
    private Auction auction;

    @BeforeEach
    void setUp() {
        adminService = new AdminServiceImpl(
                productRepository,
                productImageRepository,
                orderRepository,
                paymentRepository,
                auctionRepository,
                userRepository,
                reportRepository,
                categoryRepository,
                notificationService,
                webSocketPublisher,
                clock
        );

        activeUser = User.builder()
                .id(USER_ID)
                .fullName("Nguyen Van A")
                .email("a@example.com")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .tokenVersion(2)
                .build();

        product = Product.builder()
                .id(PRODUCT_ID)
                .sellerId(SELLER_ID)
                .categoryId(1L)
                .title("Dong ho co")
                .description("Mo ta")
                .condition(ProductCondition.GOOD)
                .status(ProductStatus.IN_AUCTION)
                .build();

        auction = Auction.builder()
                .id(AUCTION_ID)
                .productId(PRODUCT_ID)
                .sellerId(SELLER_ID)
                .status(AuctionStatus.ACTIVE)
                .startPrice(BigDecimal.valueOf(500000))
                .bidIncrement(BigDecimal.valueOf(10000))
                .currentPrice(BigDecimal.valueOf(540000))
                .highestBidderId(BIDDER_ID)
                .bidCount(3)
                .startTime(NOW.minusSeconds(3600))
                .endTime(NOW.plusSeconds(3600))
                .build();
    }

    @Test
    @DisplayName("banUser: updates status without locking users, locks auctions -> orders -> products, notifies correctly")
    void banUser_CancelsAuctionsAndPendingOrders_InLockOrder() {
        User seller = User.builder()
                .id(SELLER_ID)
                .fullName("Seller")
                .email("seller@example.com")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        Order pendingOrder = Order.builder()
                .id(ORDER_ID)
                .auctionId(99L)
                .productId(11L)
                .sellerId(SELLER_ID)
                .buyerId(BIDDER_ID)
                .finalPrice(BigDecimal.valueOf(600000))
                .status(OrderStatus.PENDING_PAYMENT)
                .build();
        Product prodForOrder = Product.builder()
                .id(11L)
                .sellerId(SELLER_ID)
                .status(ProductStatus.RESERVED)
                .build();

        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller));
        when(userRepository.findById(BIDDER_ID)).thenReturn(Optional.of(User.builder().id(BIDDER_ID).fullName("Le Minh").build()));
        when(auctionRepository.findIdsBySellerIdAndStatusInOrderByIdAsc(eq(SELLER_ID), anyList()))
                .thenReturn(List.of(AUCTION_ID));
        when(auctionRepository.findAllByIdInForUpdateOrderByIdAsc(List.of(AUCTION_ID)))
                .thenReturn(List.of(auction));
        when(orderRepository.findUnpaidOrderIdsByUserOrderByIdAsc(SELLER_ID))
                .thenReturn(List.of(ORDER_ID));
        when(orderRepository.findAllByIdInForUpdateOrderByIdAsc(List.of(ORDER_ID)))
                .thenReturn(List.of(pendingOrder));
        when(productRepository.findAllByIdInForUpdateOrderByIdAsc(List.of(PRODUCT_ID, 11L)))
                .thenReturn(List.of(product, prodForOrder));

        AdminBanUserResponse res = adminService.banUser(adminUser, SELLER_ID, "Vi pham");

        assertThat(res.id()).isEqualTo(SELLER_ID);
        assertThat(res.status()).isEqualTo(UserStatus.BANNED);
        assertThat(res.cancelledAuctions()).isEqualTo(1);
        assertThat(res.cancelledOrders()).isEqualTo(1);
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.AVAILABLE);
        assertThat(prodForOrder.getStatus()).isEqualTo(ProductStatus.AVAILABLE);

        InOrder inOrder = inOrder(userRepository, auctionRepository, orderRepository, productRepository);
        inOrder.verify(userRepository).updateStatus(SELLER_ID, UserStatus.BANNED);
        inOrder.verify(auctionRepository).findAllByIdInForUpdateOrderByIdAsc(List.of(AUCTION_ID));
        inOrder.verify(orderRepository).findAllByIdInForUpdateOrderByIdAsc(List.of(ORDER_ID));
        inOrder.verify(productRepository).findAllByIdInForUpdateOrderByIdAsc(List.of(PRODUCT_ID, 11L));

        // AUCTION_CANCELLED to seller and leader
        verify(notificationService).notify(eq(SELLER_ID), eq(NotificationType.AUCTION_CANCELLED), anyString(), anyString(), eq("AUCTION"), eq(AUCTION_ID));
        verify(notificationService).notify(eq(BIDDER_ID), eq(NotificationType.AUCTION_CANCELLED), anyString(), anyString(), eq("AUCTION"), eq(AUCTION_ID));
        // ORDER_UPDATE only to the other party (buyer)
        verify(notificationService).notify(eq(BIDDER_ID), eq(NotificationType.ORDER_UPDATE), anyString(), anyString(), eq("ORDER"), eq(ORDER_ID));
        verify(notificationService, never()).notify(eq(SELLER_ID), eq(NotificationType.ORDER_UPDATE), anyString(), anyString(), eq("ORDER"), eq(ORDER_ID));
        verify(webSocketPublisher).publishAuctionEvent(any(AuctionEvent.class));
    }

    @Test
    @DisplayName("banUser: rejects ADMIN (CANNOT_BAN_ADMIN) and already BANNED (INVALID_STATUS_TRANSITION)")
    void banUser_InvalidCases() {
        User otherAdmin = User.builder().id(88L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        when(userRepository.findById(88L)).thenReturn(Optional.of(otherAdmin));
        assertThatThrownBy(() -> adminService.banUser(adminUser, 88L, "test"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.CANNOT_BAN_ADMIN);

        activeUser.setStatus(UserStatus.BANNED);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));
        assertThatThrownBy(() -> adminService.banUser(adminUser, USER_ID, "test"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("unbanUser: transitions BANNED -> ACTIVE without changing tokenVersion")
    void unbanUser_SuccessAndInvalidTransition() {
        activeUser.setStatus(UserStatus.BANNED);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));

        AdminUnbanUserResponse res = adminService.unbanUser(adminUser, USER_ID, "Mo khoa");
        assertThat(res.id()).isEqualTo(USER_ID);
        assertThat(res.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(activeUser.getTokenVersion()).isEqualTo(2);
        verify(userRepository).updateStatus(USER_ID, UserStatus.ACTIVE);

        activeUser.setStatus(UserStatus.ACTIVE);
        assertThatThrownBy(() -> adminService.unbanUser(adminUser, USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("hideProduct: locks auctions -> orders -> product, cancels open auctions & pending orders, hides product")
    void hideProduct_Success() {
        Order pendingOrder = Order.builder()
                .id(ORDER_ID)
                .productId(PRODUCT_ID)
                .buyerId(BIDDER_ID)
                .sellerId(SELLER_ID)
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        when(auctionRepository.findIdsByProductIdAndStatusInOrderByIdAsc(eq(PRODUCT_ID), anyList()))
                .thenReturn(List.of(AUCTION_ID));
        when(orderRepository.findIdsByProductIdAndStatusInOrderByIdAsc(eq(PRODUCT_ID), anyList()))
                .thenReturn(List.of(ORDER_ID));
        when(auctionRepository.findAllByIdInForUpdateOrderByIdAsc(List.of(AUCTION_ID)))
                .thenReturn(List.of(auction));
        when(orderRepository.findAllByIdInForUpdateOrderByIdAsc(List.of(ORDER_ID)))
                .thenReturn(List.of(pendingOrder));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(userRepository.findById(BIDDER_ID)).thenReturn(Optional.of(User.builder().id(BIDDER_ID).fullName("Le Minh").build()));

        ProductHideResponse res = adminService.hideProduct(adminUser, PRODUCT_ID, "Vi pham");

        assertThat(res.id()).isEqualTo(PRODUCT_ID);
        assertThat(res.status()).isEqualTo(ProductStatus.HIDDEN);
        assertThat(res.cancelledAuctions()).isEqualTo(1);
        assertThat(res.cancelledOrders()).isEqualTo(1);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        InOrder inOrder = inOrder(auctionRepository, orderRepository, productRepository);
        inOrder.verify(auctionRepository).findAllByIdInForUpdateOrderByIdAsc(List.of(AUCTION_ID));
        inOrder.verify(orderRepository).findAllByIdInForUpdateOrderByIdAsc(List.of(ORDER_ID));
        inOrder.verify(productRepository).findByIdForUpdate(PRODUCT_ID);

        verify(notificationService).notify(eq(BIDDER_ID), eq(NotificationType.ORDER_UPDATE), anyString(), anyString(), eq("ORDER"), eq(ORDER_ID));
        verify(notificationService).notify(eq(SELLER_ID), eq(NotificationType.ORDER_UPDATE), anyString(), anyString(), eq("ORDER"), eq(ORDER_ID));
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"HIDDEN", "SOLD"})
    @DisplayName("hideProduct: already HIDDEN or SOLD throws INVALID_STATUS_TRANSITION")
    void hideProduct_InvalidStatus(ProductStatus invalidStatus) {
        product.setStatus(invalidStatus);
        when(auctionRepository.findIdsByProductIdAndStatusInOrderByIdAsc(eq(PRODUCT_ID), anyList())).thenReturn(List.of());
        when(orderRepository.findIdsByProductIdAndStatusInOrderByIdAsc(eq(PRODUCT_ID), anyList())).thenReturn(List.of());
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> adminService.hideProduct(adminUser, PRODUCT_ID, "reason"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("hideProduct: throws CONFLICT when concurrent open auction or pending order appears after locking product (HIGH-01)")
    void hideProduct_ConcurrentOpenAuctionOrPendingOrder_ThrowsConflict() {
        when(auctionRepository.findIdsByProductIdAndStatusInOrderByIdAsc(eq(PRODUCT_ID), anyList())).thenReturn(List.of());
        when(orderRepository.findIdsByProductIdAndStatusInOrderByIdAsc(eq(PRODUCT_ID), anyList())).thenReturn(List.of());
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(auctionRepository.existsByProductIdAndStatusIn(eq(PRODUCT_ID), anyList())).thenReturn(true);

        assertThatThrownBy(() -> adminService.hideProduct(adminUser, PRODUCT_ID, "Vi pham"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.CONFLICT);
        assertThat(product.getStatus()).isNotEqualTo(ProductStatus.HIDDEN);
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelAuction: cancels active auction with bids, releases product, notifies seller & leader")
    void cancelAuction_Success() {
        when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(Category.builder().id(1L).name("Watches").build()));
        when(productImageRepository.findByProductIdOrderByIdAsc(PRODUCT_ID)).thenReturn(List.of());
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(User.builder().id(SELLER_ID).fullName("Seller").build()));
        when(userRepository.findById(BIDDER_ID)).thenReturn(Optional.of(User.builder().id(BIDDER_ID).fullName("Leader").build()));
        when(orderRepository.findByAuctionId(AUCTION_ID)).thenReturn(Optional.empty());

        AdminAuctionDetailResponse res = adminService.cancelAuction(adminUser, AUCTION_ID, new AdminCancelAuctionRequest("Vi pham chinh sach"));

        assertThat(res.status()).isEqualTo(AuctionStatus.CANCELLED);
        assertThat(res.cancelReason()).isEqualTo("Vi pham chinh sach");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.AVAILABLE);
        verify(notificationService).notify(eq(SELLER_ID), eq(NotificationType.AUCTION_CANCELLED), anyString(), anyString(), eq("AUCTION"), eq(AUCTION_ID));
        verify(notificationService).notify(eq(BIDDER_ID), eq(NotificationType.AUCTION_CANCELLED), anyString(), anyString(), eq("AUCTION"), eq(AUCTION_ID));
        verify(webSocketPublisher).publishAuctionEvent(any(AuctionEvent.class));
    }

    @Test
    @DisplayName("cancelOrder: cancels PENDING_PAYMENT, releases product, notifies both parties")
    void cancelOrder_PendingPayment_Success() {
        product.setStatus(ProductStatus.RESERVED);
        Order order = Order.builder()
                .id(ORDER_ID)
                .auctionId(AUCTION_ID)
                .productId(PRODUCT_ID)
                .buyerId(BIDDER_ID)
                .sellerId(SELLER_ID)
                .finalPrice(BigDecimal.valueOf(540000))
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productImageRepository.findByProductIdOrderByIdAsc(PRODUCT_ID)).thenReturn(List.of());
        when(userRepository.findById(BIDDER_ID)).thenReturn(Optional.of(User.builder().id(BIDDER_ID).fullName("Buyer").build()));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(User.builder().id(SELLER_ID).fullName("Seller").build()));
        when(paymentRepository.findByOrderIdOrderByAttemptNoDesc(ORDER_ID)).thenReturn(List.of());

        AdminOrderDetailResponse res = adminService.cancelOrder(adminUser, ORDER_ID, new AdminCancelOrderRequest("Huy don"));

        assertThat(res.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(res.cancelReason()).isEqualTo("Huy don");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.AVAILABLE);
        verify(notificationService).notify(eq(BIDDER_ID), eq(NotificationType.ORDER_UPDATE), anyString(), anyString(), eq("ORDER"), eq(ORDER_ID));
        verify(notificationService).notify(eq(SELLER_ID), eq(NotificationType.ORDER_UPDATE), anyString(), anyString(), eq("ORDER"), eq(ORDER_ID));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "SHIPPING", "COMPLETED", "CANCELLED", "EXPIRED"})
    @DisplayName("cancelOrder: non-PENDING_PAYMENT throws INVALID_STATUS_TRANSITION (BR-14)")
    void cancelOrder_NonPendingPayment_Throws(OrderStatus status) {
        Order order = Order.builder().id(ORDER_ID).productId(PRODUCT_ID).status(status).build();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> adminService.cancelOrder(adminUser, ORDER_ID, new AdminCancelOrderRequest("Huy don")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("getStatistics: returns cumulative stats with all status keys initialized to 0")
    void getStatistics_ReturnsAllKeys() {
        when(userRepository.count()).thenReturn(25L);
        when(userRepository.countByStatus(UserStatus.BANNED)).thenReturn(2L);
        when(auctionRepository.countGroupedByStatus()).thenReturn(List.of(
                new Object[]{AuctionStatus.ACTIVE, 5L},
                new Object[]{AuctionStatus.ENDED, 10L}
        ));
        when(orderRepository.countGroupedByStatus()).thenReturn(List.<Object[]>of(
                new Object[]{OrderStatus.COMPLETED, 8L}
        ));
        when(orderRepository.sumCompletedOrderValue()).thenReturn(BigDecimal.valueOf(15000000));
        when(reportRepository.countByStatus(ReportStatus.PENDING)).thenReturn(3L);

        AdminStatisticsResponse res = adminService.getStatistics(adminUser);

        assertThat(res.totalUsers()).isEqualTo(25L);
        assertThat(res.bannedUsers()).isEqualTo(2L);
        assertThat(res.auctionsByStatus()).containsOnlyKeys(AuctionStatus.values());
        assertThat(res.auctionsByStatus().get(AuctionStatus.ACTIVE)).isEqualTo(5L);
        assertThat(res.auctionsByStatus().get(AuctionStatus.SCHEDULED)).isEqualTo(0L);
        assertThat(res.ordersByStatus()).containsOnlyKeys(OrderStatus.values());
        assertThat(res.ordersByStatus().get(OrderStatus.COMPLETED)).isEqualTo(8L);
        assertThat(res.ordersByStatus().get(OrderStatus.PENDING_PAYMENT)).isEqualTo(0L);
        assertThat(res.completedOrderValue()).isEqualByComparingTo(BigDecimal.valueOf(15000000));
        assertThat(res.pendingReports()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Non-admin caller gets FORBIDDEN and invalid sort gets VALIDATION_ERROR")
    void securityAndSortWhitelist() {
        assertThatThrownBy(() -> adminService.getUsers(regularUser, null, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        assertThatThrownBy(() -> adminService.getUsers(adminUser, null, null, null, PageRequest.of(0, 10, Sort.by("passwordHash"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("getProducts: returns AdminProductItemResponse with seller, images, activeAuctionId, everAuctioned")
    void getProducts_MapsDerivedFields() {
        when(productRepository.findAdminProducts(eq("dong ho"), eq(1L), eq(ProductStatus.IN_AUCTION), any()))
                .thenReturn(new PageImpl<>(List.of(product)));
        when(categoryRepository.findAllById(Set.of(1L)))
                .thenReturn(List.of(Category.builder().id(1L).name("Watches").build()));
        when(userRepository.findAllById(Set.of(SELLER_ID)))
                .thenReturn(List.of(User.builder().id(SELLER_ID).fullName("Seller A").build()));
        when(productImageRepository.findByProductIdInOrderByIdAsc(List.of(PRODUCT_ID)))
                .thenReturn(List.of(ProductImage.builder().id(1L).productId(PRODUCT_ID).imageUrl("https://img/1.jpg").isPrimary(true).build()));
        when(auctionRepository.findByProductIdInAndStatusIn(eq(List.of(PRODUCT_ID)), anyList()))
                .thenReturn(List.of(auction));
        when(auctionRepository.findProductIdsWithAnyAuction(List.of(PRODUCT_ID)))
                .thenReturn(Set.of(PRODUCT_ID));

        var res = adminService.getProducts(adminUser, "  dong ho  ", 1L, ProductStatus.IN_AUCTION, PageRequest.of(0, 10));
        assertThat(res.getContent()).hasSize(1);
        AdminProductItemResponse item = res.getContent().get(0);
        assertThat(item.primaryImageUrl()).isEqualTo("https://img/1.jpg");
        assertThat(item.imageCount()).isEqualTo(1);
        assertThat(item.activeAuctionId()).isEqualTo(AUCTION_ID);
        assertThat(item.everAuctioned()).isTrue();
        assertThat(item.seller().fullName()).isEqualTo("Seller A");
    }
}
