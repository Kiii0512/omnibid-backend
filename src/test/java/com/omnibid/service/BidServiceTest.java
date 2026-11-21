package com.omnibid.service;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.dto.bid.BidRequest;
import com.omnibid.dto.bid.PlaceBidResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Bid;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.impl.BidServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WebSocketPublisher webSocketPublisher;
    @Mock
    private NotificationService notificationService;

    private BidServiceImpl bidService;

    private Auction sampleAuction;
    private Product sampleProduct;
    private User sampleBidder;

    @BeforeEach
    void setUp() {
        bidService = new BidServiceImpl(
                auctionRepository,
                productRepository,
                bidRepository,
                userRepository,
                webSocketPublisher,
                notificationService,
                null,
                clock
        );

        sampleBidder = User.builder()
                .id(2L)
                .fullName("Nguyen Van A")
                .email("bidder@example.com")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        sampleProduct = Product.builder()
                .id(10L)
                .sellerId(1L)
                .status(ProductStatus.IN_AUCTION)
                .build();

        sampleAuction = Auction.builder()
                .id(100L)
                .productId(10L)
                .sellerId(1L)
                .startPrice(BigDecimal.valueOf(100))
                .bidIncrement(BigDecimal.valueOf(10))
                .currentPrice(BigDecimal.valueOf(100))
                .highestBidderId(null)
                .bidCount(0)
                .startTime(NOW.minus(1, ChronoUnit.HOURS))
                .endTime(NOW.plus(2, ChronoUnit.HOURS))
                .status(AuctionStatus.ACTIVE)
                .build();
    }

    @Test
    void placeBid_FirstBid_Success() {
        BidRequest request = new BidRequest(BigDecimal.valueOf(100));

        Bid savedBid = Bid.builder()
                .id(50L)
                .auctionId(100L)
                .bidderId(2L)
                .amount(BigDecimal.valueOf(100))
                .createdAt(NOW)
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleBidder));
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(bidRepository.saveAndFlush(any(Bid.class))).thenReturn(savedBid);

        PlaceBidResponse response = bidService.placeBid(2L, 100L, request);

        assertNotNull(response);
        assertEquals(50L, response.bidId());
        assertEquals(BigDecimal.valueOf(100), response.amount());
        assertEquals(BigDecimal.valueOf(100), sampleAuction.getCurrentPrice());
        assertEquals(2L, sampleAuction.getHighestBidderId());
        assertEquals(1, sampleAuction.getBidCount());
    }

    @Test
    @DisplayName("D-02 Người bán tự đặt giá bị SELF_BID")
    void placeBid_SellerCannotBid_ThrowsSelfBid() {
        BidRequest request = new BidRequest(BigDecimal.valueOf(120));

        User seller = User.builder().id(1L).status(UserStatus.ACTIVE).fullName("Seller").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        assertThatThrownBy(() -> bidService.placeBid(1L, 100L, request))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.SELF_BID_NOT_ALLOWED);
    }

    @Test
    @DisplayName("D-02 Đang dẫn đầu đặt tiếp bị ALREADY_LEADING")
    void placeBid_CurrentHighestBidderCannotBid_ThrowsAlreadyLeading() {
        sampleAuction.setHighestBidderId(2L);
        sampleAuction.setBidCount(1);

        BidRequest request = new BidRequest(BigDecimal.valueOf(120));

        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleBidder));
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        assertThatThrownBy(() -> bidService.placeBid(2L, 100L, request))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.ALREADY_LEADING);
    }

    @Test
    @DisplayName("D-02 Đặt giá không đủ bước giá bị BID_TOO_LOW")
    void placeBid_AmountBelowIncrement_ThrowsBidTooLow() {
        sampleAuction.setBidCount(1);
        sampleAuction.setCurrentPrice(BigDecimal.valueOf(100));
        sampleAuction.setHighestBidderId(3L);

        BidRequest request = new BidRequest(BigDecimal.valueOf(105)); // Cần ít nhất 110

        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleBidder));
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        assertThatThrownBy(() -> bidService.placeBid(2L, 100L, request))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.BID_TOO_LOW);
    }

    @Test
    @DisplayName("UT-CORE-01 Đặt giá khi bidder BANNED bị ACCOUNT_BANNED")
    void placeBid_BannedBidder_ThrowsForbidden() {
        sampleBidder.setStatus(UserStatus.BANNED);
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleBidder));

        BidRequest request = new BidRequest(BigDecimal.valueOf(120));

        assertThatThrownBy(() -> bidService.placeBid(2L, 100L, request))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.ACCOUNT_BANNED);
    }

    @Test
    @DisplayName("UT-CORE-06 Đặt giá trúng mức đã có (đua ở DB): DataIntegrityViolationException -> 409 BID_TOO_LOW")
    void placeBid_DuplicateAmountRaceCondition_ThrowsBidTooLow() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleBidder));
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(bidRepository.saveAndFlush(any(Bid.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'uq_bids_auction_amount'"));

        BidRequest request = new BidRequest(BigDecimal.valueOf(100));

        assertThatThrownBy(() -> bidService.placeBid(2L, 100L, request))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.CONFLICT);
    }
}
