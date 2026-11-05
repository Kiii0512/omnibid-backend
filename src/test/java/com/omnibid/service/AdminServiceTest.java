package com.omnibid.service;

import com.omnibid.common.AppException;
import com.omnibid.dto.admin.*;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.order.OrderResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));

    private static final Long ADMIN_ID = 9L;
    private static final Long OTHER_ID = 3L;
    private static final Long BIDDER_B_ID = 4L;
    private static final Long SELLER_ID = 1L;
    private static final Long AUCTION_ID = 12L;
    private static final Long ORDER_ID = 9L;
    private static final Long PRODUCT_ID = 10L;

    private final CurrentUser adminUser = new CurrentUser(ADMIN_ID, true);

    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private WebSocketPublisher webSocketPublisher;

    private AdminServiceImpl adminService;

    private User otherUser;
    private User adminEntity;
    private Product product;
    private Auction auction;

    @BeforeEach
    void setUp() {
        adminService = new AdminServiceImpl(
                productRepository,
                orderRepository,
                paymentRepository,
                auctionRepository,
                userRepository,
                bidRepository,
                shipmentRepository,
                reportRepository,
                auditLogRepository,
                categoryRepository,
                auditService,
                notificationService,
                webSocketPublisher,
                clock
        );

        otherUser = User.builder()
                .id(OTHER_ID)
                .fullName("Le Other")
                .email("other@example.com")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        adminEntity = User.builder()
                .id(ADMIN_ID)
                .fullName("Admin System")
                .email("admin@example.com")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        product = Product.builder()
                .id(PRODUCT_ID)
                .sellerId(SELLER_ID)
                .title("San pham test")
                .status(ProductStatus.AVAILABLE)
                .build();

        auction = Auction.builder()
                .id(AUCTION_ID)
                .productId(PRODUCT_ID)
                .sellerId(SELLER_ID)
                .status(AuctionStatus.ACTIVE)
                .startPrice(BigDecimal.valueOf(500000))
                .currentPrice(BigDecimal.valueOf(540000))
                .highestBidderId(OTHER_ID)
                .bidCount(3)
                .build();
    }

    @Test
    @DisplayName("UT-ADM-01: Khóa user cơ bản — OTHER ACTIVE, không Bid/phiên/đơn -> BANNED; audit USER_BAN")
    void updateUserStatus_BanBasicUser_Success() {
        when(userRepository.findByIdForUpdate(OTHER_ID)).thenReturn(Optional.of(otherUser));
        when(bidRepository.findDistinctAuctionIdsByBidderIdAndStatusIn(eq(OTHER_ID), anyCollection()))
                .thenReturn(List.of());
        when(auctionRepository.findBySellerIdAndStatusIn(eq(OTHER_ID), anyCollection()))
                .thenReturn(List.of());
        when(productRepository.findBySellerIdAndStatusIn(eq(OTHER_ID), anyCollection()))
                .thenReturn(List.of());
        when(orderRepository.findUnpaidOrdersByUser(OTHER_ID)).thenReturn(List.of());

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.BANNED, "Vi pham quy dinh");
        UserStatusUpdateResponse response = adminService.updateUserStatus(adminUser, OTHER_ID, request);

        assertThat(response.status()).isEqualTo(UserStatus.BANNED);
        assertThat(otherUser.getStatus()).isEqualTo(UserStatus.BANNED);
        verify(userRepository).save(otherUser);
        verify(auditService).log(ADMIN_ID, "USER_BAN", "USER", OTHER_ID, "Vi pham quy dinh");
    }

    @Test
    @DisplayName("UT-ADM-02: Khóa người đang dẫn đầu — Bid OTHER INVALIDATED; Bid BIDDER_B 530k LEADING; current_price=530k, bid_count=1")
    void updateUserStatus_BanHighestBidder_ReselectsLeaderAndPrice() {
        when(userRepository.findByIdForUpdate(OTHER_ID)).thenReturn(Optional.of(otherUser));
        when(bidRepository.findDistinctAuctionIdsByBidderIdAndStatusIn(eq(OTHER_ID), anyCollection()))
                .thenReturn(List.of(AUCTION_ID));
        when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));

        Bid bid1Other = Bid.builder().id(1L).auctionId(AUCTION_ID).bidderId(OTHER_ID).amount(BigDecimal.valueOf(520000)).status(BidStatus.OUTBID).build();
        Bid bid3Other = Bid.builder().id(3L).auctionId(AUCTION_ID).bidderId(OTHER_ID).amount(BigDecimal.valueOf(540000)).status(BidStatus.LEADING).build();
        when(bidRepository.findByAuctionIdAndBidderId(AUCTION_ID, OTHER_ID)).thenReturn(List.of(bid1Other, bid3Other));

        Bid bid2BidderB = Bid.builder().id(2L).auctionId(AUCTION_ID).bidderId(BIDDER_B_ID).amount(BigDecimal.valueOf(530000)).status(BidStatus.OUTBID).build();
        when(bidRepository.findValidBidsDesc(eq(AUCTION_ID), anyCollection(), eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(bid2BidderB));
        when(bidRepository.findValidBidsDesc(eq(AUCTION_ID), anyCollection(), eq(Pageable.unpaged())))
                .thenReturn(List.of(bid2BidderB));

        when(auctionRepository.findBySellerIdAndStatusIn(eq(OTHER_ID), anyCollection())).thenReturn(List.of());
        when(productRepository.findBySellerIdAndStatusIn(eq(OTHER_ID), anyCollection())).thenReturn(List.of());
        when(orderRepository.findUnpaidOrdersByUser(OTHER_ID)).thenReturn(List.of());

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.BANNED, "Gian lan dat gia");
        adminService.updateUserStatus(adminUser, OTHER_ID, request);

        assertThat(bid1Other.getStatus()).isEqualTo(BidStatus.INVALIDATED);
        assertThat(bid1Other.getInvalidReason()).isEqualTo("ACCOUNT_BANNED");
        assertThat(bid3Other.getStatus()).isEqualTo(BidStatus.INVALIDATED);
        assertThat(bid3Other.getInvalidReason()).isEqualTo("ACCOUNT_BANNED");

        assertThat(bid2BidderB.getStatus()).isEqualTo(BidStatus.LEADING);
        assertThat(auction.getCurrentPrice()).isEqualByComparingTo(BigDecimal.valueOf(530000));
        assertThat(auction.getHighestBidderId()).isEqualTo(BIDDER_B_ID);
        assertThat(auction.getBidCount()).isEqualTo(1);

        verify(webSocketPublisher).publishAuctionUpdate(eq(AUCTION_ID), any());
    }

    @Test
    @DisplayName("UT-ADM-03: Khóa người dẫn đầu duy nhất — Bid INVALIDATED; current_price = start_price, highest_bidder_id = null, bid_count = 0")
    void updateUserStatus_BanOnlyBidder_ResetsPriceToStartPrice() {
        when(userRepository.findByIdForUpdate(OTHER_ID)).thenReturn(Optional.of(otherUser));
        when(bidRepository.findDistinctAuctionIdsByBidderIdAndStatusIn(eq(OTHER_ID), anyCollection()))
                .thenReturn(List.of(AUCTION_ID));
        when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));

        Bid singleBid = Bid.builder().id(1L).auctionId(AUCTION_ID).bidderId(OTHER_ID).amount(BigDecimal.valueOf(540000)).status(BidStatus.LEADING).build();
        when(bidRepository.findByAuctionIdAndBidderId(AUCTION_ID, OTHER_ID)).thenReturn(List.of(singleBid));

        when(bidRepository.findValidBidsDesc(eq(AUCTION_ID), anyCollection(), eq(PageRequest.of(0, 1))))
                .thenReturn(List.of());
        when(bidRepository.findValidBidsDesc(eq(AUCTION_ID), anyCollection(), eq(Pageable.unpaged())))
                .thenReturn(List.of());

        when(auctionRepository.findBySellerIdAndStatusIn(eq(OTHER_ID), anyCollection())).thenReturn(List.of());
        when(productRepository.findBySellerIdAndStatusIn(eq(OTHER_ID), anyCollection())).thenReturn(List.of());
        when(orderRepository.findUnpaidOrdersByUser(OTHER_ID)).thenReturn(List.of());

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.BANNED, "Gian lan");
        adminService.updateUserStatus(adminUser, OTHER_ID, request);

        assertThat(singleBid.getStatus()).isEqualTo(BidStatus.INVALIDATED);
        assertThat(auction.getCurrentPrice()).isEqualByComparingTo(BigDecimal.valueOf(500000));
        assertThat(auction.getHighestBidderId()).isNull();
        assertThat(auction.getBidCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("UT-ADM-04: Khóa người không dẫn đầu — Bid OTHER INVALIDATED; current_price GIỮ 540k, highest_bidder_id GIỮ 4, bid_count 2 -> 1")
    void updateUserStatus_BanNonLeadingBidder_PriceAndLeaderRemain() {
        when(userRepository.findByIdForUpdate(OTHER_ID)).thenReturn(Optional.of(otherUser));
        when(bidRepository.findDistinctAuctionIdsByBidderIdAndStatusIn(eq(OTHER_ID), anyCollection()))
                .thenReturn(List.of(AUCTION_ID));

        auction.setHighestBidderId(BIDDER_B_ID);
        auction.setBidCount(2);
        when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));

        Bid bidOther = Bid.builder().id(1L).auctionId(AUCTION_ID).bidderId(OTHER_ID).amount(BigDecimal.valueOf(520000)).status(BidStatus.OUTBID).build();
        when(bidRepository.findByAuctionIdAndBidderId(AUCTION_ID, OTHER_ID)).thenReturn(List.of(bidOther));

        Bid bidBidderB = Bid.builder().id(2L).auctionId(AUCTION_ID).bidderId(BIDDER_B_ID).amount(BigDecimal.valueOf(540000)).status(BidStatus.LEADING).build();
        when(bidRepository.findValidBidsDesc(eq(AUCTION_ID), anyCollection(), eq(Pageable.unpaged())))
                .thenReturn(List.of(bidBidderB));

        when(auctionRepository.findBySellerIdAndStatusIn(eq(OTHER_ID), anyCollection())).thenReturn(List.of());
        when(productRepository.findBySellerIdAndStatusIn(eq(OTHER_ID), anyCollection())).thenReturn(List.of());
        when(orderRepository.findUnpaidOrdersByUser(OTHER_ID)).thenReturn(List.of());

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.BANNED, "Khong dan dau");
        adminService.updateUserStatus(adminUser, OTHER_ID, request);

        assertThat(bidOther.getStatus()).isEqualTo(BidStatus.INVALIDATED);
        assertThat(auction.getCurrentPrice()).isEqualByComparingTo(BigDecimal.valueOf(540000));
        assertThat(auction.getHighestBidderId()).isEqualTo(BIDDER_B_ID);
        assertThat(auction.getBidCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("UT-ADM-05: Khóa buyer có đơn chưa thanh toán — Order CANCELLED; Payment FAILED; Product AVAILABLE; thông báo seller")
    void updateUserStatus_BanBuyerWithPendingPaymentOrder_CancelsOrderAndRestoresProduct() {
        when(userRepository.findByIdForUpdate(OTHER_ID)).thenReturn(Optional.of(otherUser));
        when(bidRepository.findDistinctAuctionIdsByBidderIdAndStatusIn(eq(OTHER_ID), anyCollection())).thenReturn(List.of());
        when(auctionRepository.findBySellerIdAndStatusIn(eq(OTHER_ID), anyCollection())).thenReturn(List.of());
        when(productRepository.findBySellerIdAndStatusIn(eq(OTHER_ID), anyCollection())).thenReturn(List.of());

        Order pendingOrder = Order.builder()
                .id(ORDER_ID)
                .buyerId(OTHER_ID)
                .sellerId(SELLER_ID)
                .productId(PRODUCT_ID)
                .status(OrderStatus.PENDING_PAYMENT)
                .build();
        when(orderRepository.findUnpaidOrdersByUser(OTHER_ID)).thenReturn(List.of(pendingOrder));

        product.setStatus(ProductStatus.RESERVED);
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(pendingOrder));

        Payment pendingPayment = Payment.builder().id(100L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build();
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(pendingPayment));

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.BANNED, "Chua thanh toan");
        adminService.updateUserStatus(adminUser, OTHER_ID, request);

        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(pendingOrder.getCancelReason()).isEqualTo("ACCOUNT_BANNED");
        assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(pendingPayment.getFailureReason()).isEqualTo("ORDER_CANCELLED");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.AVAILABLE);

        verify(auditService).log(ADMIN_ID, "ORDER_CANCEL", "ORDER", ORDER_ID, "ACCOUNT_BANNED");
        verify(notificationService).notify(eq(SELLER_ID), eq(NotificationType.ORDER_UPDATE), anyString(), anyString(), eq("ORDER"), eq(ORDER_ID));
    }

    @Test
    @DisplayName("UT-ADM-06: Khóa seller — Hủy phiên SCHEDULED & ACTIVE, Bid LEADING->NOT_WON, Product->HIDDEN, Order PENDING_PAYMENT CANCELLED, Order PAID không đổi")
    void updateUserStatus_BanSeller_CancelsAuctionsHidesProductsCancelsUnpaidOrdersKeepsPaid() {
        User seller = User.builder().id(SELLER_ID).status(UserStatus.ACTIVE).role(UserRole.USER).build();
        when(userRepository.findByIdForUpdate(SELLER_ID)).thenReturn(Optional.of(seller));
        when(bidRepository.findDistinctAuctionIdsByBidderIdAndStatusIn(eq(SELLER_ID), anyCollection())).thenReturn(List.of());

        Auction scheduledAuction = Auction.builder().id(11L).sellerId(SELLER_ID).status(AuctionStatus.SCHEDULED).build();
        Auction activeAuction = Auction.builder().id(12L).sellerId(SELLER_ID).status(AuctionStatus.ACTIVE).build();
        when(auctionRepository.findBySellerIdAndStatusIn(eq(SELLER_ID), anyCollection()))
                .thenReturn(List.of(scheduledAuction, activeAuction));
        when(auctionRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(scheduledAuction));
        when(auctionRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(activeAuction));

        Bid leadingBid = Bid.builder().id(5L).auctionId(12L).bidderId(OTHER_ID).status(BidStatus.LEADING).build();
        when(bidRepository.findByAuctionId(11L)).thenReturn(List.of());
        when(bidRepository.findByAuctionId(12L)).thenReturn(List.of(leadingBid));

        Product prod1 = Product.builder().id(21L).sellerId(SELLER_ID).status(ProductStatus.AVAILABLE).build();
        when(productRepository.findBySellerIdAndStatusIn(eq(SELLER_ID), anyCollection())).thenReturn(List.of(prod1));
        when(productRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(prod1));

        Order pendingOrder = Order.builder().id(31L).sellerId(SELLER_ID).buyerId(OTHER_ID).productId(21L).status(OrderStatus.PENDING_PAYMENT).build();
        when(orderRepository.findUnpaidOrdersByUser(SELLER_ID)).thenReturn(List.of(pendingOrder));
        when(orderRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(pendingOrder));

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.BANNED, "Khoa seller");
        adminService.updateUserStatus(adminUser, SELLER_ID, request);

        assertThat(scheduledAuction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        assertThat(activeAuction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        assertThat(leadingBid.getStatus()).isEqualTo(BidStatus.NOT_WON);
        assertThat(prod1.getStatus()).isEqualTo(ProductStatus.HIDDEN);
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("UT-ADM-07: Thứ tự khóa — User có Bid ở phiên id 15 và 12 -> findByIdForUpdate theo thứ tự 12 rồi 15; User khóa SAU CÙNG")
    void updateUserStatus_LockOrder_AuctionsInAscendingOrderBeforeUser() {
        when(userRepository.findByIdForUpdate(OTHER_ID)).thenReturn(Optional.of(otherUser));
        when(bidRepository.findDistinctAuctionIdsByBidderIdAndStatusIn(eq(OTHER_ID), anyCollection()))
                .thenReturn(List.of(15L, 12L)); // unsorted input

        Auction auc12 = Auction.builder().id(12L).status(AuctionStatus.ACTIVE).startPrice(BigDecimal.valueOf(100)).build();
        Auction auc15 = Auction.builder().id(15L).status(AuctionStatus.ACTIVE).startPrice(BigDecimal.valueOf(100)).build();
        when(auctionRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(auc12));
        when(auctionRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(auc15));

        when(auctionRepository.findBySellerIdAndStatusIn(eq(OTHER_ID), anyCollection())).thenReturn(List.of());
        when(productRepository.findBySellerIdAndStatusIn(eq(OTHER_ID), anyCollection())).thenReturn(List.of());
        when(orderRepository.findUnpaidOrdersByUser(OTHER_ID)).thenReturn(List.of());

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.BANNED, "Thu tu khoa");
        adminService.updateUserStatus(adminUser, OTHER_ID, request);

        InOrder inOrder = inOrder(auctionRepository, userRepository);
        inOrder.verify(auctionRepository).findByIdForUpdate(12L);
        inOrder.verify(auctionRepository).findByIdForUpdate(15L);
        inOrder.verify(userRepository).findByIdForUpdate(OTHER_ID);
    }

    @Test
    @DisplayName("UT-ADM-08: Ẩn sản phẩm — Product HIDDEN; phiên CANCELLED (PRODUCT_HIDDEN); ORDER PENDING_PAYMENT CANCELLED; audit PRODUCT_HIDE")
    void hideProduct_ActiveAuctionAndUnpaidOrder_CancelsAuctionAndOrder_HidesProduct() {
        when(auctionRepository.findByProductIdAndStatusIn(eq(PRODUCT_ID), anyCollection())).thenReturn(List.of(auction));
        when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));

        Order pendingOrder = Order.builder().id(ORDER_ID).productId(PRODUCT_ID).status(OrderStatus.PENDING_PAYMENT).buyerId(OTHER_ID).build();
        when(orderRepository.findByProductIdAndStatusIn(eq(PRODUCT_ID), anyCollection())).thenReturn(List.of(pendingOrder));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(pendingOrder));

        ProductHideRequest request = new ProductHideRequest(ProductStatus.HIDDEN, "Hang vi pham");
        ProductHideResponse response = adminService.hideProduct(adminUser, PRODUCT_ID, request);

        assertThat(response.status()).isEqualTo(ProductStatus.HIDDEN);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        assertThat(auction.getCancelReason()).isEqualTo("PRODUCT_HIDDEN");
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        verify(auditService).log(ADMIN_ID, "AUCTION_CANCEL", "AUCTION", AUCTION_ID, "PRODUCT_HIDDEN");
        verify(auditService).log(ADMIN_ID, "ORDER_CANCEL", "ORDER", ORDER_ID, "PRODUCT_HIDDEN");
        verify(auditService).log(ADMIN_ID, "PRODUCT_HIDE", "PRODUCT", PRODUCT_ID, "Hang vi pham");
    }

    @Test
    @DisplayName("UT-ADM-09: Admin hủy phiên có Bid — CANCELLED, closed_at=NOW, LEADING->NOT_WON, 2 OUTBID giữ, không tạo Order, Product AVAILABLE")
    void cancelAuction_AdminCancelsActiveAuctionWithBids_Success() {
        when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));

        Bid b1 = Bid.builder().id(1L).status(BidStatus.OUTBID).build();
        Bid b2 = Bid.builder().id(2L).status(BidStatus.OUTBID).build();
        Bid b3 = Bid.builder().id(3L).status(BidStatus.LEADING).build();
        when(bidRepository.findByAuctionId(AUCTION_ID)).thenReturn(List.of(b1, b2, b3));

        AdminCancelAuctionRequest request = new AdminCancelAuctionRequest("Huy phien co bid");
        AuctionResponse response = adminService.cancelAuction(adminUser, AUCTION_ID, request);

        assertThat(response.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        assertThat(auction.getClosedAt()).isEqualTo(NOW);
        assertThat(b3.getStatus()).isEqualTo(BidStatus.NOT_WON);
        assertThat(b1.getStatus()).isEqualTo(BidStatus.OUTBID);
        assertThat(b2.getStatus()).isEqualTo(BidStatus.OUTBID);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.AVAILABLE);

        verify(auditService).log(ADMIN_ID, "AUCTION_CANCEL", "AUCTION", AUCTION_ID, "Huy phien co bid");
        verifyNoInteractions(orderRepository); // Không tạo Order!
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING_PAYMENT", "PAID", "SHIPPING"})
    @DisplayName("UT-ADM-10: Admin hủy đơn — ORDER CANCELLED, Payment PENDING->FAILED, SUCCESS giữ, Shipment giữ, Product AVAILABLE, audit ORDER_CANCEL")
    void cancelOrder_AllowedStatuses_CancelsSuccessfully(OrderStatus orderStatus) {
        Order order = Order.builder()
                .id(ORDER_ID)
                .productId(PRODUCT_ID)
                .buyerId(OTHER_ID)
                .sellerId(SELLER_ID)
                .status(orderStatus)
                .build();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));

        Payment pendingPayment = Payment.builder().id(1L).orderId(ORDER_ID).status(PaymentStatus.PENDING).build();
        Payment successPayment = Payment.builder().id(2L).orderId(ORDER_ID).status(PaymentStatus.SUCCESS).build();
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(pendingPayment, successPayment));

        AdminCancelOrderRequest request = new AdminCancelOrderRequest("Admin huy don");
        OrderResponse response = adminService.cancelOrder(adminUser, ORDER_ID, request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isEqualTo(NOW);
        assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(pendingPayment.getFailureReason()).isEqualTo("ORDER_CANCELLED");
        assertThat(successPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.AVAILABLE);

        verify(auditService).log(ADMIN_ID, "ORDER_CANCEL", "ORDER", ORDER_ID, "Admin huy don");
        verify(notificationService).notify(eq(OTHER_ID), eq(NotificationType.ORDER_UPDATE), anyString(), anyString(), eq("ORDER"), eq(ORDER_ID));
        verify(notificationService).notify(eq(SELLER_ID), eq(NotificationType.ORDER_UPDATE), anyString(), anyString(), eq("ORDER"), eq(ORDER_ID));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"COMPLETED", "CANCELLED", "EXPIRED"})
    @DisplayName("UT-ADM-11: Hủy đơn đã đóng — ORDER COMPLETED, CANCELLED, EXPIRED -> 409 INVALID_STATUS_TRANSITION")
    void cancelOrder_ClosedOrders_ThrowsConflict(OrderStatus closedStatus) {
        Order order = Order.builder().id(ORDER_ID).productId(PRODUCT_ID).status(closedStatus).build();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));

        AdminCancelOrderRequest request = new AdminCancelOrderRequest("Huy don da dong");
        assertThatThrownBy(() -> adminService.cancelOrder(adminUser, ORDER_ID, request))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("INVALID_STATUS_TRANSITION") && ((AppException) e).getStatus() == HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("UT-ADM-12: Không khóa Admin / chính mình / người đã khóa — 409 CANNOT_BAN_ADMIN / 409 INVALID_STATUS_TRANSITION")
    void updateUserStatus_CannotBanAdminOrSelfOrAlreadyBanned() {
        // Case 1: Đối tượng là chính mình
        UserStatusUpdateRequest req = new UserStatusUpdateRequest(UserStatus.BANNED, "Tu khoa");
        assertThatThrownBy(() -> adminService.updateUserStatus(adminUser, ADMIN_ID, req))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("CANNOT_BAN_ADMIN") && ((AppException) e).getStatus() == HttpStatus.CONFLICT);

        // Case 2: Đối tượng là Admin khác
        User otherAdmin = User.builder().id(88L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        when(userRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(otherAdmin));
        assertThatThrownBy(() -> adminService.updateUserStatus(adminUser, 88L, req))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("CANNOT_BAN_ADMIN") && ((AppException) e).getStatus() == HttpStatus.CONFLICT);

        // Case 3: Đối tượng đã BANNED
        otherUser.setStatus(UserStatus.BANNED);
        when(userRepository.findByIdForUpdate(OTHER_ID)).thenReturn(Optional.of(otherUser));
        assertThatThrownBy(() -> adminService.updateUserStatus(adminUser, OTHER_ID, req))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("INVALID_STATUS_TRANSITION") && ((AppException) e).getStatus() == HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("UT-ADM-13: Mở khóa — OTHER BANNED, có Bid INVALIDATED -> ACTIVE; Bid giữ INVALIDATED; audit USER_UNBAN")
    void updateUserStatus_UnbanUser_Success() {
        otherUser.setStatus(UserStatus.BANNED);
        when(userRepository.findByIdForUpdate(OTHER_ID)).thenReturn(Optional.of(otherUser));

        UserStatusUpdateRequest req = new UserStatusUpdateRequest(UserStatus.ACTIVE, "Mo khoa tai khoan");
        UserStatusUpdateResponse response = adminService.updateUserStatus(adminUser, OTHER_ID, req);

        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(otherUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(auditService).log(ADMIN_ID, "USER_UNBAN", "USER", OTHER_ID, "Mo khoa tai khoan");
        verifyNoInteractions(bidRepository); // Không khôi phục Bid!
    }

    @Test
    @DisplayName("UT-ADM-14: Audit log đi cùng thao tác — Khóa seller (2 hủy phiên + 1 hủy đơn) -> 4 dòng audit cùng admin_id")
    void updateUserStatus_BanSeller_GeneratesFourAuditLogsWithSameAdminId() {
        User seller = User.builder().id(SELLER_ID).status(UserStatus.ACTIVE).role(UserRole.USER).build();
        when(userRepository.findByIdForUpdate(SELLER_ID)).thenReturn(Optional.of(seller));
        when(bidRepository.findDistinctAuctionIdsByBidderIdAndStatusIn(eq(SELLER_ID), anyCollection())).thenReturn(List.of());

        Auction auc1 = Auction.builder().id(11L).sellerId(SELLER_ID).status(AuctionStatus.SCHEDULED).build();
        Auction auc2 = Auction.builder().id(12L).sellerId(SELLER_ID).status(AuctionStatus.ACTIVE).build();
        when(auctionRepository.findBySellerIdAndStatusIn(eq(SELLER_ID), anyCollection())).thenReturn(List.of(auc1, auc2));
        when(auctionRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(auc1));
        when(auctionRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(auc2));

        when(productRepository.findBySellerIdAndStatusIn(eq(SELLER_ID), anyCollection())).thenReturn(List.of());

        Order pendingOrder = Order.builder().id(31L).sellerId(SELLER_ID).buyerId(OTHER_ID).productId(PRODUCT_ID).status(OrderStatus.PENDING_PAYMENT).build();
        when(orderRepository.findUnpaidOrdersByUser(SELLER_ID)).thenReturn(List.of(pendingOrder));
        when(orderRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(pendingOrder));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.BANNED, "Khoa seller audit");
        adminService.updateUserStatus(adminUser, SELLER_ID, request);

        // 4 dòng audit: 2x AUCTION_CANCEL + 1x ORDER_CANCEL + 1x USER_BAN
        verify(auditService).log(eq(ADMIN_ID), eq("AUCTION_CANCEL"), eq("AUCTION"), eq(11L), eq("ACCOUNT_BANNED"));
        verify(auditService).log(eq(ADMIN_ID), eq("AUCTION_CANCEL"), eq("AUCTION"), eq(12L), eq("ACCOUNT_BANNED"));
        verify(auditService).log(eq(ADMIN_ID), eq("ORDER_CANCEL"), eq("ORDER"), eq(31L), eq("ACCOUNT_BANNED"));
        verify(auditService).log(eq(ADMIN_ID), eq("USER_BAN"), eq("USER"), eq(SELLER_ID), eq("Khoa seller audit"));
    }

    @Test
    @DisplayName("UT-ADM-17: Thống kê — ENDED 30, UNSOLD 9; Payment SUCCESS 31, FAILED 12 -> sellThroughRate = 30/39, successRate = 31/43; toàn 0 -> 0.0; revenue.simulated = true")
    void getStatistics_CalculatesRatesCorrectly_HandlesZeroDivision() {
        when(auctionRepository.countByStatus(AuctionStatus.ENDED)).thenReturn(30L);
        when(auctionRepository.countByStatus(AuctionStatus.UNSOLD)).thenReturn(9L);
        when(auctionRepository.countByStatus(AuctionStatus.SCHEDULED)).thenReturn(4L);
        when(auctionRepository.countByStatus(AuctionStatus.ACTIVE)).thenReturn(12L);
        when(auctionRepository.countByStatus(AuctionStatus.CANCELLED)).thenReturn(6L);

        when(paymentRepository.countByStatus(PaymentStatus.SUCCESS)).thenReturn(31L);
        when(paymentRepository.countByStatus(PaymentStatus.FAILED)).thenReturn(12L);
        when(paymentRepository.countByStatus(PaymentStatus.PENDING)).thenReturn(0L);

        when(orderRepository.sumCompletedRevenue(any(), any())).thenReturn(BigDecimal.valueOf(18450000.00));

        AdminStatisticsResponse response = adminService.getStatistics(adminUser, NOW.minusDays(30), NOW);

        assertThat(response.revenue().simulated()).isTrue();
        assertThat(response.revenue().completedOrderTotal()).isEqualByComparingTo(BigDecimal.valueOf(18450000.00));

        // 30 / (30 + 9) = 30 / 39 = 0.7692... -> 0.77
        assertThat(response.auctions().sellThroughRate()).isEqualTo(0.77);

        // 31 / (31 + 12) = 31 / 43 = 0.7209... -> 0.72
        assertThat(response.payments().successRate()).isEqualTo(0.72);

        // Trường hợp toàn 0
        reset(auctionRepository, paymentRepository, orderRepository);
        when(auctionRepository.countByStatus(any())).thenReturn(0L);
        when(paymentRepository.countByStatus(any())).thenReturn(0L);
        when(orderRepository.sumCompletedRevenue(any(), any())).thenReturn(BigDecimal.ZERO);

        AdminStatisticsResponse zeroResponse = adminService.getStatistics(adminUser, NOW.minusDays(30), NOW);
        assertThat(zeroResponse.auctions().sellThroughRate()).isEqualTo(0.0);
        assertThat(zeroResponse.payments().successRate()).isEqualTo(0.0);
        assertThat(zeroResponse.bids().avgPerAuction()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Admin queries: getUsers, getProducts, getOrders, getAuditLogs success & non-admin 403")
    void adminQueries_SuccessAndNonAdminForbidden() {
        CurrentUser nonAdmin = new CurrentUser(2L, false);

        // 1. Non-admin forbidden
        assertThatThrownBy(() -> adminService.getUsers(nonAdmin, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(AppException.class).matches(e -> ((AppException) e).getCode().equals("FORBIDDEN"));
        assertThatThrownBy(() -> adminService.getProducts(nonAdmin, null, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(AppException.class).matches(e -> ((AppException) e).getCode().equals("FORBIDDEN"));
        assertThatThrownBy(() -> adminService.getOrders(nonAdmin, null, null, null, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(AppException.class).matches(e -> ((AppException) e).getCode().equals("FORBIDDEN"));
        assertThatThrownBy(() -> adminService.getAuditLogs(nonAdmin, null, null, null, null, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(AppException.class).matches(e -> ((AppException) e).getCode().equals("FORBIDDEN"));

        // 2. getUsers
        when(userRepository.findAdminUsers(eq(UserStatus.ACTIVE), eq("John"), any()))
                .thenReturn(new PageImpl<>(List.of(otherUser)));
        var usersRes = adminService.getUsers(adminUser, UserStatus.ACTIVE, "John", PageRequest.of(0, 10));
        assertThat(usersRes.getContent()).hasSize(1);

        // 3. getProducts
        when(productRepository.findAdminProducts(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(product)));
        when(userRepository.findById(product.getSellerId())).thenReturn(Optional.of(otherUser));
        when(categoryRepository.findById(any())).thenReturn(Optional.of(Category.builder().id(1L).name("Watches").build()));
        var prodsRes = adminService.getProducts(adminUser, null, null, null, PageRequest.of(0, 10));
        assertThat(prodsRes.getContent()).hasSize(1);
        assertThat(prodsRes.getContent().get(0).categoryName()).isEqualTo("Watches");

        // 4. getOrders
        Order sampleOrder = Order.builder().id(9L).buyerId(OTHER_ID).sellerId(SELLER_ID).status(OrderStatus.PAID).finalPrice(BigDecimal.valueOf(500000)).build();
        when(orderRepository.findAdminOrders(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleOrder)));
        when(userRepository.findById(OTHER_ID)).thenReturn(Optional.of(otherUser));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(User.builder().id(SELLER_ID).fullName("Seller").build()));
        var ordersRes = adminService.getOrders(adminUser, null, null, null, null, null, PageRequest.of(0, 10));
        assertThat(ordersRes.getContent()).hasSize(1);

        // 5. getAuditLogs
        AuditLog auditLog = AuditLog.builder().id(1L).adminId(ADMIN_ID).action("USER_BAN").targetType("USER").targetId(3L).createdAt(NOW).build();
        when(auditLogRepository.findAdminAuditLogs(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(auditLog)));
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminEntity));
        var auditRes = adminService.getAuditLogs(adminUser, null, null, null, null, null, null, PageRequest.of(0, 10));
        assertThat(auditRes.getContent()).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"HIDDEN", "SOLD", "REMOVED"})
    @DisplayName("hideProduct on product already HIDDEN/SOLD/REMOVED throws 409 INVALID_STATUS_TRANSITION")
    void hideProduct_InvalidStatus_ThrowsConflict(ProductStatus invalidStatus) {
        Product p = Product.builder().id(PRODUCT_ID).sellerId(SELLER_ID).status(invalidStatus).build();
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> adminService.hideProduct(adminUser, PRODUCT_ID, new ProductHideRequest(invalidStatus, "Ly do an san pham hop le")))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("INVALID_STATUS_TRANSITION") && ((AppException) e).getStatus() == HttpStatus.CONFLICT);
    }

    @ParameterizedTest
    @EnumSource(value = AuctionStatus.class, names = {"CANCELLED", "ENDED"})
    @DisplayName("cancelAuction on auction already CANCELLED/ENDED throws 409 INVALID_STATUS_TRANSITION")
    void cancelAuction_InvalidStatus_ThrowsConflict(AuctionStatus invalidStatus) {
        Auction a = Auction.builder().id(AUCTION_ID).sellerId(SELLER_ID).productId(PRODUCT_ID).status(invalidStatus).build();
        when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> adminService.cancelAuction(adminUser, AUCTION_ID, new AdminCancelAuctionRequest("Ly do huy phien hop le")))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getCode().equals("INVALID_STATUS_TRANSITION") && ((AppException) e).getStatus() == HttpStatus.CONFLICT);
    }
}
