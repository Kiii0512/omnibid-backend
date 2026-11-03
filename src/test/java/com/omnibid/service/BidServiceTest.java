package com.omnibid.service;

import com.omnibid.common.AppException;
import com.omnibid.dto.bid.BidRequest;
import com.omnibid.dto.bid.BidResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Bid;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.BidStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.impl.BidServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WebSocketPublisher webSocketPublisher;

    private BidServiceImpl bidService;

    private Auction sampleAuction;
    private User sampleBidder;

    @BeforeEach
    void setUp() {
        bidService = new BidServiceImpl(auctionRepository, bidRepository, userRepository, webSocketPublisher, clock);

        sampleBidder = User.builder()
                .id(2L)
                .fullName("Nguyen Van A")
                .email("bidder@example.com")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
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
                .startTime(NOW.minusHours(1))
                .endTime(NOW.plusHours(2))
                .status(AuctionStatus.ACTIVE)
                .build();
    }

    @Test
    void placeBid_FirstBid_Success() {
        BidRequest request = BidRequest.builder()
                .auctionId(100L)
                .amount(BigDecimal.valueOf(100))
                .build();

        Bid savedBid = Bid.builder()
                .id(50L)
                .auctionId(100L)
                .bidderId(2L)
                .amount(BigDecimal.valueOf(100))
                .status(BidStatus.LEADING)
                .createdAt(NOW)
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleBidder));
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(bidRepository.findTopByAuctionIdAndStatusOrderByAmountDesc(100L, BidStatus.LEADING))
                .thenReturn(Optional.empty());
        when(bidRepository.save(any(Bid.class))).thenReturn(savedBid);

        BidResponse response = bidService.placeBid(2L, request);

        assertNotNull(response);
        assertEquals(50L, response.getId());
        assertEquals(BigDecimal.valueOf(100), response.getAmount());
        assertEquals(BidStatus.LEADING, response.getStatus());
        assertEquals("N***A", response.getBidderName());

        assertEquals(BigDecimal.valueOf(100), sampleAuction.getCurrentPrice());
        assertEquals(2L, sampleAuction.getHighestBidderId());
        assertEquals(1, sampleAuction.getBidCount());

        verify(webSocketPublisher).publishAuctionUpdate(eq(100L), any(BidResponse.class));
    }

    @Test
    @DisplayName("D-02 Người bán tự đặt giá bị 422 SELF_BID_NOT_ALLOWED")
    void placeBid_SellerCannotBid_ThrowsBadRequest() {
        BidRequest request = BidRequest.builder()
                .auctionId(100L)
                .amount(BigDecimal.valueOf(120))
                .build();

        User seller = User.builder().id(1L).status(UserStatus.ACTIVE).fullName("Seller").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(seller));
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));

        AppException ex = assertThrows(AppException.class, () -> bidService.placeBid(1L, request));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("SELF_BID_NOT_ALLOWED", ex.getCode());
    }

    @Test
    @DisplayName("D-02 Đang dẫn đầu đặt tiếp bị 409 ALREADY_LEADING")
    void placeBid_CurrentHighestBidderCannotBid_ThrowsBadRequest() {
        sampleAuction.setHighestBidderId(2L);
        sampleAuction.setBidCount(1);

        BidRequest request = BidRequest.builder()
                .auctionId(100L)
                .amount(BigDecimal.valueOf(120))
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleBidder));
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));

        AppException ex = assertThrows(AppException.class, () -> bidService.placeBid(2L, request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("ALREADY_LEADING", ex.getCode());
    }

    @Test
    @DisplayName("D-02 Đặt giá không đủ bước giá bị 409 BID_TOO_LOW")
    void placeBid_AmountBelowIncrement_ThrowsBadRequest() {
        sampleAuction.setBidCount(1);
        sampleAuction.setCurrentPrice(BigDecimal.valueOf(100));
        sampleAuction.setHighestBidderId(3L);

        BidRequest request = BidRequest.builder()
                .auctionId(100L)
                .amount(BigDecimal.valueOf(105)) // Requires at least 110 (100 + 10)
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleBidder));
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));

        AppException ex = assertThrows(AppException.class, () -> bidService.placeBid(2L, request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("BID_TOO_LOW", ex.getCode());
        assertNotNull(ex.getData());
    }

    @Test
    @DisplayName("UT-CORE-01 Đặt giá khi bidder BANNED bị 403 ACCOUNT_BANNED")
    void placeBid_BannedBidder_ThrowsForbidden() {
        sampleBidder.setStatus(UserStatus.BANNED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleBidder));

        BidRequest request = BidRequest.builder()
                .auctionId(100L)
                .amount(BigDecimal.valueOf(120))
                .build();

        AppException ex = assertThrows(AppException.class, () -> bidService.placeBid(2L, request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("ACCOUNT_BANNED", ex.getCode());
        verifyNoInteractions(auctionRepository);
    }
}
