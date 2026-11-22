package com.omnibid.service;

import com.omnibid.config.AppProperties;
import com.omnibid.dto.websocket.AuctionEvent;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionEventType;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.PaymentRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.security.OrderAccessPolicy;
import com.omnibid.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionCloserTest {

    private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderAccessPolicy orderAccessPolicy;
    @Mock
    private OrderLifecycleSupport orderLifecycleSupport;
    @Mock
    private WebSocketPublisher webSocketPublisher;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private OrderService orderService;
    private AuctionCloser auctionCloser;

    private Auction sampleAuction;
    private Product sampleProduct;
    private User activeLeader;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        orderService = new OrderServiceImpl(
                orderRepository,
                productRepository,
                productImageRepository,
                userRepository,
                paymentRepository,
                orderAccessPolicy,
                orderLifecycleSupport,
                appProperties,
                clock,
                notificationService
        );

        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        auctionCloser = new AuctionCloser(
                transactionTemplate,
                auctionRepository,
                productRepository,
                userRepository,
                orderService,
                webSocketPublisher,
                notificationService,
                clock
        );

        activeLeader = User.builder()
                .id(2L)
                .fullName("Nguyen Minh")
                .status(UserStatus.ACTIVE)
                .build();

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
                .startTime(NOW.minus(2, ChronoUnit.HOURS))
                .endTime(NOW)
                .status(AuctionStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("BR-09: Chốt phiên có người thắng ACTIVE -> ENDED, Product RESERVED, tạo Order, AUCTION_RESULT, WS AUCTION_ENDED")
    void closeOne_WithActiveWinner_ClosesAuctionAndCreatesOrder() {
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(userRepository.findById(2L)).thenReturn(Optional.of(activeLeader));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findByAuctionId(100L)).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(9L);
            return o;
        });

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.ENDED, sampleAuction.getStatus());
        assertEquals(2L, sampleAuction.getWinnerId());
        assertEquals(BigDecimal.valueOf(540000), sampleAuction.getFinalPrice());
        assertEquals(NOW, sampleAuction.getClosedAt());
        assertEquals(ProductStatus.RESERVED, sampleProduct.getStatus());

        verify(orderRepository).save(argThat(order ->
                order.getAuctionId().equals(100L)
                        && order.getBuyerId().equals(2L)
                        && order.getFinalPrice().compareTo(BigDecimal.valueOf(540000)) == 0
                        && order.getPaymentDeadline().equals(NOW.plus(48, ChronoUnit.HOURS))
        ));

        verify(notificationService).notify(eq(2L), eq(NotificationType.AUCTION_RESULT), any(), any(), eq("AUCTION"), eq(100L));
        verify(notificationService).notify(eq(1L), eq(NotificationType.AUCTION_RESULT), any(), any(), eq("AUCTION"), eq(100L));

        ArgumentCaptor<AuctionEvent> eventCaptor = ArgumentCaptor.forClass(AuctionEvent.class);
        verify(webSocketPublisher).publishAuctionEvent(eventCaptor.capture());
        assertEquals(AuctionEventType.AUCTION_ENDED, eventCaptor.getValue().type());
        assertEquals(AuctionStatus.ENDED, eventCaptor.getValue().status());
        assertEquals("N***h", eventCaptor.getValue().highestBidderName());
        assertNull(eventCaptor.getValue().bid());
    }

    @ParameterizedTest
    @ValueSource(strings = {"540000"})
    @NullSource
    @DisplayName("Giá sàn bằng đúng giá cuối hoặc không có giá sàn đều ENDED")
    void closeOne_ReservePriceMetOrNull_EndsAuction(String reserveStr) {
        sampleAuction.setReservePrice(reserveStr != null ? new BigDecimal(reserveStr) : null);

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(userRepository.findById(2L)).thenReturn(Optional.of(activeLeader));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findByAuctionId(100L)).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.ENDED, sampleAuction.getStatus());
        assertEquals(2L, sampleAuction.getWinnerId());
    }

    @Test
    @DisplayName("BR-10: Hạn thanh toán tính từ closed_at khi Scheduler trễ")
    void closeOne_SchedulerDelayed_DeadlineCalculatedFromClosedAt() {
        sampleAuction.setEndTime(NOW.minus(30, ChronoUnit.MINUTES));

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(userRepository.findById(2L)).thenReturn(Optional.of(activeLeader));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findByAuctionId(100L)).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        auctionCloser.closeOne(100L);

        assertEquals(NOW, sampleAuction.getClosedAt());
        verify(orderRepository).save(argThat(order ->
                order.getPaymentDeadline().equals(NOW.plus(48, ChronoUnit.HOURS))
        ));
    }

    @Test
    @DisplayName("BR-09: Người dẫn đầu bị BANNED -> phiên chuyển UNSOLD, không trao cho người thứ hai")
    void closeOne_LeadingBidderBanned_ClosesAsUnsold() {
        User bannedLeader = User.builder().id(2L).fullName("Banned User").status(UserStatus.BANNED).build();

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bannedLeader));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.UNSOLD, sampleAuction.getStatus());
        assertNull(sampleAuction.getWinnerId());
        assertNull(sampleAuction.getFinalPrice());
        assertEquals(ProductStatus.AVAILABLE, sampleProduct.getStatus());
        verifyNoInteractions(orderRepository);
        verify(notificationService).notify(eq(1L), eq(NotificationType.AUCTION_RESULT), any(), any(), eq("AUCTION"), eq(100L));
        verify(notificationService, never()).notify(eq(2L), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Không có Bid: chuyển UNSOLD, Product AVAILABLE, không tạo Order")
    void closeOne_NoBids_UnsoldAndProductAvailable() {
        sampleAuction.setHighestBidderId(null);
        sampleAuction.setBidCount(0);

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.UNSOLD, sampleAuction.getStatus());
        assertEquals(ProductStatus.AVAILABLE, sampleProduct.getStatus());
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("Không đạt giá sàn: chuyển UNSOLD, Product AVAILABLE")
    void closeOne_ReservePriceNotMet_Unsold() {
        sampleAuction.setReservePrice(BigDecimal.valueOf(600000));

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(userRepository.findById(2L)).thenReturn(Optional.of(activeLeader));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.UNSOLD, sampleAuction.getStatus());
        assertEquals(ProductStatus.AVAILABLE, sampleProduct.getStatus());
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("BR-16: Product HIDDEN khi phiên UNSOLD vẫn giữ HIDDEN")
    void closeOne_ProductHidden_RemainsHiddenWhenUnsold() {
        sampleProduct.setStatus(ProductStatus.HIDDEN);
        sampleAuction.setHighestBidderId(null);
        sampleAuction.setBidCount(0);

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.UNSOLD, sampleAuction.getStatus());
        assertEquals(ProductStatus.HIDDEN, sampleProduct.getStatus());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Mở phiên SCHEDULED đến hạn -> ACTIVE và phát WS AUCTION_STARTED")
    void startOne_ScheduledReady_ActivatesAndPublishesEvent() {
        Auction scheduled = Auction.builder()
                .id(101L)
                .productId(10L)
                .sellerId(1L)
                .startPrice(BigDecimal.valueOf(100000))
                .bidIncrement(BigDecimal.valueOf(10000))
                .currentPrice(BigDecimal.valueOf(100000))
                .bidCount(0)
                .startTime(NOW)
                .endTime(NOW.plus(2, ChronoUnit.HOURS))
                .status(AuctionStatus.SCHEDULED)
                .build();

        when(auctionRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(scheduled));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));

        auctionCloser.startOne(101L);

        assertEquals(AuctionStatus.ACTIVE, scheduled.getStatus());
        verify(webSocketPublisher).publishAuctionEvent(argThat(ev ->
                ev.type() == AuctionEventType.AUCTION_STARTED && ev.status() == AuctionStatus.ACTIVE
        ));
    }
}
