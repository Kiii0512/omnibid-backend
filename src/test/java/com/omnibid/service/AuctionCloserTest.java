package com.omnibid.service;

import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionCloserTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderService orderService;
    @Mock
    private WebSocketPublisher webSocketPublisher;
    @Mock
    private PlatformTransactionManager transactionManager;

    private AuctionCloser auctionCloser;

    private Auction sampleAuction;
    private Product sampleProduct;
    private User sampleWinner;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        auctionCloser = new AuctionCloser(
                transactionManager,
                auctionRepository,
                productRepository,
                userRepository,
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
                .startTime(NOW.minus(2, ChronoUnit.HOURS))
                .endTime(NOW)
                .status(AuctionStatus.ACTIVE)
                .build();

        sampleWinner = User.builder()
                .id(2L)
                .fullName("Buyer One")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("UT-SCH-01 Chốt phiên có người thắng: Auction ENDED, winner, final_price, Product RESERVED, tạo Order")
    void closeOne_WithWinner_ClosesAuctionAndCreatesOrder() {
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));
        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleWinner));

        Order savedOrder = Order.builder()
                .id(9L)
                .auctionId(100L)
                .productId(10L)
                .sellerId(1L)
                .buyerId(2L)
                .finalPrice(BigDecimal.valueOf(540000))
                .status(OrderStatus.PENDING_PAYMENT)
                .build();
        when(orderService.createFromAuction(any(), any())).thenReturn(savedOrder);

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.ENDED, sampleAuction.getStatus());
        assertEquals(2L, sampleAuction.getWinnerId());
        assertEquals(BigDecimal.valueOf(540000), sampleAuction.getFinalPrice());
        assertEquals(NOW, sampleAuction.getClosedAt());
        assertEquals(ProductStatus.RESERVED, sampleProduct.getStatus());

        verify(orderService).createFromAuction(eq(sampleAuction), eq(NOW));
    }

    @ParameterizedTest
    @ValueSource(strings = {"540000"})
    @NullSource
    @DisplayName("UT-SCH-02 Giá sàn bằng đúng giá cuối hoặc không có giá sàn đều ENDED")
    void closeOne_ReservePriceMetOrNull_EndsAuction(String reserveStr) {
        BigDecimal reservePrice = reserveStr != null ? new BigDecimal(reserveStr) : null;
        sampleAuction.setReservePrice(reservePrice);

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));
        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleWinner));
        when(orderService.createFromAuction(any(), any())).thenReturn(mock(Order.class));

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.ENDED, sampleAuction.getStatus());
        assertEquals(2L, sampleAuction.getWinnerId());
    }

    @Test
    @DisplayName("UT-SCH-04 Người dẫn đầu bị khóa trước khi chốt: chuyển UNSOLD theo BR-18")
    void closeOne_LeadingBidderBanned_ClosesAsUnsold() {
        sampleWinner.setStatus(UserStatus.BANNED);

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));
        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleWinner));

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.UNSOLD, sampleAuction.getStatus());
        assertNull(sampleAuction.getWinnerId());
        assertEquals(ProductStatus.AVAILABLE, sampleProduct.getStatus());
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("UT-SCH-06 Không có Bid: chuyển UNSOLD, Product AVAILABLE, không tạo Order")
    void closeOne_NoBids_UnsoldAndProductAvailable() {
        sampleAuction.setHighestBidderId(null);
        sampleAuction.setBidCount(0);

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.UNSOLD, sampleAuction.getStatus());
        assertEquals(NOW, sampleAuction.getClosedAt());
        assertNull(sampleAuction.getWinnerId());
        assertEquals(ProductStatus.AVAILABLE, sampleProduct.getStatus());
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("UT-SCH-07 Không đạt giá sàn: chuyển UNSOLD, Product AVAILABLE")
    void closeOne_ReservePriceNotMet_Unsold() {
        sampleAuction.setReservePrice(BigDecimal.valueOf(600000));

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));
        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleWinner));

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.UNSOLD, sampleAuction.getStatus());
        assertEquals(ProductStatus.AVAILABLE, sampleProduct.getStatus());
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("UT-SCH-08 Product HIDDEN khi phiên UNSOLD: Product giữ HIDDEN")
    void closeOne_ProductHidden_RemainsHiddenWhenUnsold() {
        sampleProduct.setStatus(ProductStatus.HIDDEN);
        sampleAuction.setHighestBidderId(null);

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));

        auctionCloser.closeOne(100L);

        assertEquals(AuctionStatus.UNSOLD, sampleAuction.getStatus());
        assertEquals(ProductStatus.HIDDEN, sampleProduct.getStatus());
    }

    @Test
    @DisplayName("UT-SCH-09 Kiểm tra lại sau khóa: nếu phiên đã CANCELLED thì bỏ qua")
    void closeOne_RecheckStateAfterLock_IgnoresWhenCancelled() {
        sampleAuction.setStatus(AuctionStatus.CANCELLED);
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));

        auctionCloser.closeOne(100L);

        verify(auctionRepository, never()).save(any(Auction.class));
        verifyNoInteractions(orderService);
    }
}
