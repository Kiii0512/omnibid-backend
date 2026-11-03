package com.omnibid.service;

import com.omnibid.config.AppProperties;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Bid;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.BidStatus;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionCloserTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private WebSocketPublisher webSocketPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    private OrderService orderService;
    private AuctionCloser auctionCloser;

    private Auction sampleAuction;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        orderService = new OrderServiceImpl(orderRepository, appProperties);

        // Direct runner transaction template for unit test
        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        auctionCloser = new AuctionCloser(
                transactionTemplate,
                auctionRepository,
                bidRepository,
                productRepository,
                orderService,
                webSocketPublisher,
                clock
        );

        sampleProduct = Product.builder()
                .id(10L)
                .sellerId(1L)
                .title("Vintage Watch")
                .status(ProductStatus.IN_AUCTION)
                .build();

        sampleAuction = Auction.builder()
                .id(100L)
                .productId(10L)
                .sellerId(1L)
                .startPrice(BigDecimal.valueOf(100000))
                .bidIncrement(BigDecimal.valueOf(10000))
                .currentPrice(BigDecimal.valueOf(540000))
                .highestBidderId(2L)
                .bidCount(3)
                .startTime(NOW.minusHours(2))
                .endTime(NOW)
                .status(AuctionStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("UT-SCH-01 Chốt phiên có người thắng: Auction ENDED, winner, final_price, Bid WON, Product RESERVED, tạo Order")
    void closeOne_WithWinner_ClosesAuctionAndCreatesOrder() {
        Bid highestBid = Bid.builder()
                .id(1L)
                .auctionId(100L)
                .bidderId(2L)
                .amount(BigDecimal.valueOf(540000))
                .status(BidStatus.LEADING)
                .build();

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(bidRepository.findValidBidsDesc(eq(100L), any(), eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(highestBid));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findByAuctionId(100L)).thenReturn(Optional.empty());

        Order savedOrder = Order.builder()
                .id(9L)
                .auctionId(100L)
                .productId(10L)
                .sellerId(1L)
                .buyerId(2L)
                .finalPrice(BigDecimal.valueOf(540000))
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentDeadline(NOW.plusHours(48))
                .build();
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.ENDED, sampleAuction.getStatus());
        assertEquals(2L, sampleAuction.getWinnerId());
        assertEquals(BigDecimal.valueOf(540000), sampleAuction.getFinalPrice());
        assertEquals(NOW, sampleAuction.getClosedAt());

        assertEquals(BidStatus.WON, highestBid.getStatus());
        verify(bidRepository).save(highestBid);

        assertEquals(ProductStatus.RESERVED, sampleProduct.getStatus());
        verify(productRepository).save(sampleProduct);

        verify(orderRepository).save(argThat(order ->
                order.getAuctionId().equals(100L)
                && order.getProductId().equals(10L)
                && order.getSellerId().equals(1L)
                && order.getBuyerId().equals(2L)
                && order.getFinalPrice().compareTo(BigDecimal.valueOf(540000)) == 0
                && order.getPaymentDeadline().equals(NOW.plusHours(48))
        ));

        verify(webSocketPublisher).publishAuctionUpdate(eq(100L), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"540000"})
    @NullSource
    @DisplayName("UT-SCH-02 Giá sàn bằng đúng giá cuối hoặc không có giá sàn đều ENDED")
    void closeOne_ReservePriceMetOrNull_EndsAuction(String reserveStr) {
        BigDecimal reservePrice = reserveStr != null ? new BigDecimal(reserveStr) : null;
        sampleAuction.setReservePrice(reservePrice);

        Bid highestBid = Bid.builder()
                .id(1L)
                .auctionId(100L)
                .bidderId(2L)
                .amount(BigDecimal.valueOf(540000))
                .status(BidStatus.LEADING)
                .build();

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(bidRepository.findValidBidsDesc(eq(100L), any(), eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(highestBid));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findByAuctionId(100L)).thenReturn(Optional.empty());

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.ENDED, sampleAuction.getStatus());
        assertEquals(2L, sampleAuction.getWinnerId());
    }

    @Test
    @DisplayName("UT-SCH-03 Hạn thanh toán tính từ closed_at khi Scheduler trễ")
    void closeOne_SchedulerDelayed_DeadlineCalculatedFromClosedAt() {
        // Auction ended 30 minutes ago, but closed at NOW
        sampleAuction.setEndTime(NOW.minusMinutes(30));

        Bid highestBid = Bid.builder()
                .id(1L)
                .auctionId(100L)
                .bidderId(2L)
                .amount(BigDecimal.valueOf(540000))
                .status(BidStatus.LEADING)
                .build();

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(bidRepository.findValidBidsDesc(eq(100L), any(), eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(highestBid));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findByAuctionId(100L)).thenReturn(Optional.empty());

        auctionCloser.closeOne(100L);

        assertEquals(NOW, sampleAuction.getClosedAt());
        verify(orderRepository).save(argThat(order ->
                order.getPaymentDeadline().equals(NOW.plusHours(48)) // closedAt + 48h
        ));
    }

    @Test
    @DisplayName("UT-SCH-04 Người dẫn đầu bị khóa trước khi chốt: chọn Bid hợp lệ kế tiếp")
    void closeOne_LeadingBidderBanned_SelectsNextValidBidder() {
        // Valid bids excludes banned user bid, returning next valid bid of BIDDER_B (id=4, amount=520000)
        Bid secondBid = Bid.builder()
                .id(2L)
                .auctionId(100L)
                .bidderId(4L)
                .amount(BigDecimal.valueOf(520000))
                .status(BidStatus.OUTBID)
                .build();

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(bidRepository.findValidBidsDesc(eq(100L), any(), eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(secondBid));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findByAuctionId(100L)).thenReturn(Optional.empty());

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.ENDED, sampleAuction.getStatus());
        assertEquals(4L, sampleAuction.getWinnerId());
        assertEquals(BigDecimal.valueOf(520000), sampleAuction.getFinalPrice());
        assertEquals(BidStatus.WON, secondBid.getStatus());
    }

    @Test
    @DisplayName("UT-SCH-05 Chốt khi Order đã tồn tại: không tạo Order mới, không lỗi")
    void closeOne_OrderAlreadyExists_Idempotent() {
        Bid highestBid = Bid.builder()
                .id(1L)
                .auctionId(100L)
                .bidderId(2L)
                .amount(BigDecimal.valueOf(540000))
                .status(BidStatus.LEADING)
                .build();

        Order existingOrder = Order.builder()
                .id(9L)
                .auctionId(100L)
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(bidRepository.findValidBidsDesc(eq(100L), any(), eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(highestBid));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findByAuctionId(100L)).thenReturn(Optional.of(existingOrder));

        assertDoesNotThrow(() -> auctionCloser.closeOne(100L));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("UT-SCH-06 Không có Bid: chuyển UNSOLD, Product AVAILABLE, không tạo Order")
    void closeOne_NoBids_UnsoldAndProductAvailable() {
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(bidRepository.findValidBidsDesc(eq(100L), any(), eq(PageRequest.of(0, 1))))
                .thenReturn(Collections.emptyList());
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.UNSOLD, sampleAuction.getStatus());
        assertEquals(NOW, sampleAuction.getClosedAt());
        assertNull(sampleAuction.getWinnerId());
        assertEquals(ProductStatus.AVAILABLE, sampleProduct.getStatus());
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("UT-SCH-07 Không đạt giá sàn: chuyển UNSOLD, Bid NOT_WON, Product AVAILABLE")
    void closeOne_ReservePriceNotMet_UnsoldAndBidNotWon() {
        sampleAuction.setReservePrice(BigDecimal.valueOf(600000));

        Bid highestBid = Bid.builder()
                .id(1L)
                .auctionId(100L)
                .bidderId(2L)
                .amount(BigDecimal.valueOf(540000))
                .status(BidStatus.LEADING)
                .build();

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(bidRepository.findValidBidsDesc(eq(100L), any(), eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(highestBid));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.UNSOLD, sampleAuction.getStatus());
        assertEquals(BidStatus.NOT_WON, highestBid.getStatus());
        assertEquals(ProductStatus.AVAILABLE, sampleProduct.getStatus());
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("UT-SCH-08 Product HIDDEN khi phiên UNSOLD: Product giữ HIDDEN")
    void closeOne_ProductHidden_RemainsHiddenWhenUnsold() {
        sampleProduct.setStatus(ProductStatus.HIDDEN);

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(bidRepository.findValidBidsDesc(eq(100L), any(), eq(PageRequest.of(0, 1))))
                .thenReturn(Collections.emptyList());
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.UNSOLD, sampleAuction.getStatus());
        assertEquals(ProductStatus.HIDDEN, sampleProduct.getStatus());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("UT-SCH-09 Kiểm tra lại sau khóa: nếu phiên đã CANCELLED hoặc chưa hết giờ thì bỏ qua")
    void closeOne_RecheckStateAfterLock_IgnoresWhenCancelledOrNotEnded() {
        sampleAuction.setStatus(AuctionStatus.CANCELLED);
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));

        auctionCloser.closeOne(100L);

        verify(auctionRepository, never()).save(any(Auction.class));
        verifyNoInteractions(bidRepository);
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("UT-SCH-10 Thông báo sau chốt: đăng ký WebSocket publish sau khi commit")
    void closeOne_NotifiesWebSocketUpdate() {
        Bid highestBid = Bid.builder()
                .id(1L)
                .auctionId(100L)
                .bidderId(2L)
                .amount(BigDecimal.valueOf(540000))
                .status(BidStatus.LEADING)
                .build();

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(bidRepository.findValidBidsDesc(eq(100L), any(), eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(highestBid));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findByAuctionId(100L)).thenReturn(Optional.empty());

        auctionCloser.closeOne(100L);

        verify(webSocketPublisher).publishAuctionUpdate(eq(100L), any());
    }
}
