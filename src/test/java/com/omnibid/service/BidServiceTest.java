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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WebSocketPublisher webSocketPublisher;

    @InjectMocks
    private BidServiceImpl bidService;

    private Auction sampleAuction;
    private User sampleBidder;

    @BeforeEach
    void setUp() {
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
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusHours(2))
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
                .createdAt(LocalDateTime.now())
                .build();

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(bidRepository.findTopByAuctionIdAndStatusOrderByAmountDesc(100L, BidStatus.LEADING))
                .thenReturn(Optional.empty());
        when(bidRepository.save(any(Bid.class))).thenReturn(savedBid);
        when(userRepository.findById(2L)).thenReturn(Optional.of(sampleBidder));

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
    void placeBid_SellerCannotBid_ThrowsBadRequest() {
        BidRequest request = BidRequest.builder()
                .auctionId(100L)
                .amount(BigDecimal.valueOf(120))
                .build();

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));

        AppException ex = assertThrows(AppException.class, () -> bidService.placeBid(1L, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Seller cannot bid on their own auction", ex.getMessage());
    }

    @Test
    void placeBid_CurrentHighestBidderCannotBid_ThrowsBadRequest() {
        sampleAuction.setHighestBidderId(2L);
        sampleAuction.setBidCount(1);

        BidRequest request = BidRequest.builder()
                .auctionId(100L)
                .amount(BigDecimal.valueOf(120))
                .build();

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));

        AppException ex = assertThrows(AppException.class, () -> bidService.placeBid(2L, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("You are already the highest bidder", ex.getMessage());
    }

    @Test
    void placeBid_AmountBelowIncrement_ThrowsBadRequest() {
        sampleAuction.setBidCount(1);
        sampleAuction.setCurrentPrice(BigDecimal.valueOf(100));
        sampleAuction.setHighestBidderId(3L);

        BidRequest request = BidRequest.builder()
                .auctionId(100L)
                .amount(BigDecimal.valueOf(105)) // Requires at least 110 (100 + 10)
                .build();

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));

        AppException ex = assertThrows(AppException.class, () -> bidService.placeBid(2L, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("must be at least"));
    }
}

