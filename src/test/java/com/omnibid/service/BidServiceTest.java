package com.omnibid.service;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.dto.bid.BidRequest;
import com.omnibid.dto.bid.PlaceBidResponse;
import com.omnibid.dto.websocket.AuctionEvent;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Bid;
import com.omnibid.entity.Product;
import com.omnibid.enums.AuctionEventType;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.impl.BidServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

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
class BidServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WebSocketPublisher webSocketPublisher;
    @Mock
    private NotificationService notificationService;

    private BidServiceImpl bidService;
    private Auction sampleAuction;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        bidService = new BidServiceImpl(
                auctionRepository,
                bidRepository,
                productRepository,
                productImageRepository,
                orderRepository,
                userRepository,
                webSocketPublisher,
                notificationService,
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
    @DisplayName("Đặt giá đầu tiên thành công: không truy vấn users, phát AuctionEvent BID_PLACED")
    void placeBid_FirstBid_Success() {
        BidRequest request = new BidRequest(BigDecimal.valueOf(100));
        Bid savedBid = Bid.builder()
                .id(50L)
                .auctionId(100L)
                .bidderId(2L)
                .amount(BigDecimal.valueOf(100))
                .createdAt(NOW)
                .build();

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(bidRepository.save(any(Bid.class))).thenReturn(savedBid);

        PlaceBidResponse response = bidService.placeBid(100L, 2L, "Nguyen Van Anh", request);

        assertNotNull(response);
        assertEquals(50L, response.bidId());
        assertEquals(100L, response.auctionId());
        assertEquals(BigDecimal.valueOf(100), response.amount());
        assertEquals(BigDecimal.valueOf(100), response.currentPrice());
        assertEquals(1, response.bidCount());
        assertEquals(BigDecimal.valueOf(110), response.minNextBid());

        verifyNoInteractions(userRepository);
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());

        ArgumentCaptor<AuctionEvent> eventCaptor = ArgumentCaptor.forClass(AuctionEvent.class);
        verify(webSocketPublisher).publishAuctionEvent(eventCaptor.capture());
        AuctionEvent event = eventCaptor.getValue();
        assertEquals(AuctionEventType.BID_PLACED, event.type());
        assertEquals("N***h", event.highestBidderName());
        assertNotNull(event.bid());
        assertEquals(50L, event.bid().id());
        assertEquals("N***h", event.bid().bidderName());
    }

    @Test
    @DisplayName("Người bán tự đặt giá bị 409 SELF_BID_NOT_ALLOWED")
    void placeBid_SellerCannotBid_ThrowsConflict() {
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> bidService.placeBid(100L, 1L, "Seller", new BidRequest(BigDecimal.valueOf(120))));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(ErrorCode.SELF_BID_NOT_ALLOWED, ex.getCode());
    }

    @Test
    @DisplayName("Đang dẫn đầu đặt tiếp bị 409 ALREADY_LEADING")
    void placeBid_AlreadyLeading_ThrowsConflict() {
        sampleAuction.setHighestBidderId(2L);
        sampleAuction.setBidCount(1);
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> bidService.placeBid(100L, 2L, "Bidder", new BidRequest(BigDecimal.valueOf(120))));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(ErrorCode.ALREADY_LEADING, ex.getCode());
    }

    @Test
    @DisplayName("Đặt giá thấp hơn minNextBid bị 409 BID_TOO_LOW kèm details.minNextBid")
    void placeBid_AmountBelowIncrement_ThrowsBidTooLow() {
        sampleAuction.setBidCount(1);
        sampleAuction.setCurrentPrice(BigDecimal.valueOf(100));
        sampleAuction.setHighestBidderId(3L);
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> bidService.placeBid(100L, 2L, "Bidder", new BidRequest(BigDecimal.valueOf(105))));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(ErrorCode.BID_TOO_LOW, ex.getCode());
        assertEquals(BigDecimal.valueOf(110), ex.getDetails().get("minNextBid"));
    }

    @Test
    @DisplayName("Đặt giá trên phiên của sản phẩm HIDDEN bị 404 NOT_FOUND")
    void placeBid_HiddenProduct_ThrowsNotFound() {
        sampleProduct.setStatus(ProductStatus.HIDDEN);
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> bidService.placeBid(100L, 2L, "Bidder", new BidRequest(BigDecimal.valueOf(100))));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals(ErrorCode.NOT_FOUND, ex.getCode());
    }

    @Test
    @DisplayName("Đặt giá vượt người dẫn đầu gửi OUTBID cho leader cũ")
    void placeBid_OutbidsPreviousLeader_SendsOutbidNotification() {
        sampleAuction.setBidCount(1);
        sampleAuction.setCurrentPrice(BigDecimal.valueOf(100));
        sampleAuction.setHighestBidderId(3L);

        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(bidRepository.save(any(Bid.class))).thenAnswer(inv -> {
            Bid b = inv.getArgument(0);
            b.setId(55L);
            b.setCreatedAt(NOW);
            return b;
        });

        PlaceBidResponse response = bidService.placeBid(100L, 2L, "Bidder", new BidRequest(BigDecimal.valueOf(120)));

        assertEquals(55L, response.bidId());
        assertEquals(2, response.bidCount());
        assertEquals(BigDecimal.valueOf(130), response.minNextBid());
        verify(notificationService).notify(
                eq(3L),
                eq(NotificationType.OUTBID),
                any(),
                any(),
                eq("AUCTION"),
                eq(100L)
        );
    }

    @Test
    @DisplayName("Tranh chấp UNIQUE ở DB: DataIntegrityViolationException -> 409 CONFLICT")
    void placeBid_DuplicateAmountRaceCondition_ThrowsConflict() {
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(bidRepository.save(any(Bid.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'uq_bids_auction_amount'"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> bidService.placeBid(100L, 2L, "Bidder", new BidRequest(BigDecimal.valueOf(100))));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(ErrorCode.CONFLICT, ex.getCode());
    }
}
