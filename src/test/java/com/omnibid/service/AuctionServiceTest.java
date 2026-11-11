package com.omnibid.service;

import com.omnibid.common.AppException;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionDetailResponse;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Bid;
import com.omnibid.entity.Category;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.entity.ProductImage;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.BidStatus;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductCondition;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.mapper.AuctionMapper;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.CategoryRepository;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.impl.AuctionLifecycleServiceImpl;
import com.omnibid.service.impl.AuctionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private WebSocketPublisher webSocketPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    private AuctionMapper auctionMapper;
    private AuctionLifecycleServiceImpl auctionLifecycleService;
    private AuctionServiceImpl auctionService;

    private User sampleSeller;
    private Category sampleCategory;
    private Product sampleProduct;
    private ProductImage sampleImage;
    private Auction sampleAuction;

    @BeforeEach
    void setUp() {
        auctionMapper = new AuctionMapper(
                productRepository,
                productImageRepository,
                categoryRepository,
                userRepository
        );
        auctionLifecycleService = new AuctionLifecycleServiceImpl(
                auctionRepository,
                productRepository,
                bidRepository,
                orderRepository,
                auctionMapper,
                webSocketPublisher,
                transactionTemplate
        );
        auctionService = new AuctionServiceImpl(
                auctionRepository,
                productRepository,
                productImageRepository,
                userRepository,
                auctionMapper,
                auctionLifecycleService,
                webSocketPublisher
        );

        sampleSeller = User.builder()
                .id(1L)
                .fullName("Seller Name")
                .email("seller@example.com")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        sampleCategory = Category.builder()
                .id(5L)
                .name("Watches")
                .slug("watches")
                .build();

        sampleProduct = Product.builder()
                .id(10L)
                .sellerId(1L)
                .categoryId(5L)
                .title("Vintage Watch")
                .description("Classic automatic mechanical watch")
                .condition(ProductCondition.LIKE_NEW)
                .status(ProductStatus.AVAILABLE)
                .build();

        sampleImage = ProductImage.builder()
                .id(20L)
                .productId(10L)
                .imageUrl("https://cdn.example.com/watch.jpg")
                .publicId("products/watch.jpg")
                .isPrimary(true)
                .build();

        sampleAuction = Auction.builder()
                .id(100L)
                .productId(10L)
                .sellerId(1L)
                .startPrice(BigDecimal.valueOf(100))
                .bidIncrement(BigDecimal.valueOf(10))
                .reservePrice(BigDecimal.valueOf(200))
                .currentPrice(BigDecimal.valueOf(100))
                .bidCount(0)
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(5))
                .status(AuctionStatus.SCHEDULED)
                .build();
    }

    private void stubTransactionTemplate() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void createAuction_Success() {
        AuctionCreateRequest request = AuctionCreateRequest.builder()
                .productId(10L)
                .startPrice(BigDecimal.valueOf(100))
                .bidIncrement(BigDecimal.valueOf(10))
                .reservePrice(BigDecimal.valueOf(200))
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(5))
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(productImageRepository.countByProductId(10L)).thenReturn(1L);
        when(auctionRepository.save(any(Auction.class))).thenReturn(sampleAuction);
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(sampleCategory));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleSeller));
        when(productImageRepository.findByProductIdOrderByIsPrimaryDescCreatedAtAsc(10L))
                .thenReturn(List.of(sampleImage));

        AuctionResponse response = auctionService.createAuction(1L, request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Vintage Watch", response.getProductTitle());
        assertEquals("Watches", response.getCategoryName());
        assertEquals(5L, response.getCategoryId());
        assertEquals(ProductCondition.LIKE_NEW, response.getCondition());
        assertEquals("https://cdn.example.com/watch.jpg", response.getPrimaryImageUrl());
        assertEquals(BigDecimal.valueOf(110), response.getMinNextBid());
        assertFalse(response.getReserveReached());
        assertEquals(AuctionStatus.SCHEDULED, response.getStatus());
        verify(productRepository).save(sampleProduct);
        assertEquals(ProductStatus.IN_AUCTION, sampleProduct.getStatus());
    }

    @Test
    void createAuction_NoImage_ThrowsBadRequest() {
        AuctionCreateRequest request = AuctionCreateRequest.builder()
                .productId(10L)
                .startPrice(BigDecimal.valueOf(100))
                .bidIncrement(BigDecimal.valueOf(10))
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(5))
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(productImageRepository.countByProductId(10L)).thenReturn(0L);

        AppException ex = assertThrows(AppException.class, () -> auctionService.createAuction(1L, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void createAuction_NotOwner_ThrowsForbidden() {
        AuctionCreateRequest request = AuctionCreateRequest.builder()
                .productId(10L)
                .startPrice(BigDecimal.valueOf(100))
                .bidIncrement(BigDecimal.valueOf(10))
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(5))
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        AppException ex = assertThrows(AppException.class, () -> auctionService.createAuction(999L, request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void getAuctionById_EnrichedDtoAndReservePriceVisibility() {
        sampleAuction.setCurrentPrice(BigDecimal.valueOf(250));
        sampleAuction.setBidIncrement(BigDecimal.valueOf(25));
        sampleAuction.setReservePrice(BigDecimal.valueOf(200));

        when(auctionRepository.findById(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(sampleCategory));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleSeller));
        when(productImageRepository.findByProductIdOrderByIsPrimaryDescCreatedAtAsc(10L))
                .thenReturn(List.of(sampleImage));

        AuctionDetailResponse guestView = auctionService.getAuctionById(100L, null, false);
        assertEquals(BigDecimal.valueOf(275), guestView.getMinNextBid());
        assertTrue(guestView.getReserveReached());
        assertNull(guestView.getReservePrice());
        assertEquals("Classic automatic mechanical watch", guestView.getProductDescription());
        assertEquals(1, guestView.getImages().size());
        assertNotNull(guestView.getServerTime());

        AuctionDetailResponse sellerView = auctionService.getAuctionById(100L, 1L, false);
        assertEquals(BigDecimal.valueOf(200), sellerView.getReservePrice());

        AuctionDetailResponse adminView = auctionService.getAuctionById(100L, 999L, true);
        assertEquals(BigDecimal.valueOf(200), adminView.getReservePrice());
    }

    @Test
    void cancelAuction_Success_WhenNoBids() {
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(sampleAuction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleSeller));
        when(auctionRepository.save(any(Auction.class))).thenReturn(sampleAuction);
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        AuctionResponse response = auctionService.cancelAuction(1L, 100L, "Cancelled by seller");

        assertNotNull(response);
        assertEquals(AuctionStatus.CANCELLED, sampleAuction.getStatus());
        assertEquals(ProductStatus.AVAILABLE, sampleProduct.getStatus());
        verify(webSocketPublisher).publishAuctionUpdate(eq(100L), any(AuctionResponse.class));
    }

    @Test
    void cancelAuction_WithBids_ThrowsBadRequest() {
        sampleAuction.setBidCount(2);
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(sampleAuction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleSeller));

        AppException ex = assertThrows(AppException.class, () -> auctionService.cancelAuction(1L, 100L, "Try cancel"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void startScheduledAuctions_ActivatesAuctionAndProduct() {
        stubTransactionTemplate();
        sampleAuction.setStatus(AuctionStatus.SCHEDULED);
        sampleAuction.setStartTime(LocalDateTime.now().minusMinutes(5));
        sampleProduct.setStatus(ProductStatus.AVAILABLE);

        when(auctionRepository.findByStatusAndStartTimeLessThanEqual(eq(AuctionStatus.SCHEDULED), any()))
                .thenReturn(List.of(sampleAuction));
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(auctionRepository.save(any(Auction.class))).thenReturn(sampleAuction);
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        auctionService.startScheduledAuctions();

        assertEquals(AuctionStatus.ACTIVE, sampleAuction.getStatus());
        assertEquals(ProductStatus.IN_AUCTION, sampleProduct.getStatus());
        verify(webSocketPublisher).publishAuctionUpdate(eq(100L), any(AuctionResponse.class));
    }

    @Test
    void closeEndedAuctions_NoBids_MarksUnsoldAndProductAvailable() {
        stubTransactionTemplate();
        sampleAuction.setStatus(AuctionStatus.ACTIVE);
        sampleAuction.setEndTime(LocalDateTime.now().minusMinutes(10));
        sampleAuction.setBidCount(0);
        sampleAuction.setHighestBidderId(null);
        sampleProduct.setStatus(ProductStatus.IN_AUCTION);

        when(auctionRepository.findByStatusAndEndTimeLessThanEqual(eq(AuctionStatus.ACTIVE), any()))
                .thenReturn(List.of(sampleAuction));
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        auctionService.closeEndedAuctions();

        assertEquals(AuctionStatus.UNSOLD, sampleAuction.getStatus());
        assertNotNull(sampleAuction.getClosedAt());
        assertEquals(ProductStatus.AVAILABLE, sampleProduct.getStatus());
        verify(orderRepository, never()).save(any());
        verify(webSocketPublisher).publishAuctionUpdate(eq(100L), any(AuctionResponse.class));
    }

    @Test
    void closeEndedAuctions_ReserveNotMet_MarksUnsoldAndNotWon() {
        stubTransactionTemplate();
        sampleAuction.setStatus(AuctionStatus.ACTIVE);
        sampleAuction.setEndTime(LocalDateTime.now().minusMinutes(10));
        sampleAuction.setBidCount(2);
        sampleAuction.setHighestBidderId(2L);
        sampleAuction.setCurrentPrice(BigDecimal.valueOf(150));
        sampleAuction.setReservePrice(BigDecimal.valueOf(200));
        sampleProduct.setStatus(ProductStatus.IN_AUCTION);

        Bid leadingBid = Bid.builder()
                .id(55L)
                .auctionId(100L)
                .bidderId(2L)
                .amount(BigDecimal.valueOf(150))
                .status(BidStatus.LEADING)
                .build();

        when(auctionRepository.findByStatusAndEndTimeLessThanEqual(eq(AuctionStatus.ACTIVE), any()))
                .thenReturn(List.of(sampleAuction));
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bidRepository.findTopByAuctionIdAndStatusOrderByAmountDesc(100L, BidStatus.LEADING))
                .thenReturn(Optional.of(leadingBid));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        auctionService.closeEndedAuctions();

        assertEquals(AuctionStatus.UNSOLD, sampleAuction.getStatus());
        assertEquals(BidStatus.NOT_WON, leadingBid.getStatus());
        assertEquals(ProductStatus.AVAILABLE, sampleProduct.getStatus());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void closeEndedAuctions_WinningBidMeetsReserve_MarksEndedSoldAndCreatesOrder() {
        stubTransactionTemplate();
        Auction auction3 = Auction.builder()
                .id(3L)
                .productId(10L)
                .sellerId(1L)
                .startPrice(new BigDecimal("50000000.00"))
                .bidIncrement(new BigDecimal("1000000.00"))
                .reservePrice(new BigDecimal("60000000.00"))
                .currentPrice(new BigDecimal("63000000.00"))
                .highestBidderId(5L)
                .bidCount(3)
                .startTime(LocalDateTime.of(2026, 11, 1, 10, 0))
                .endTime(LocalDateTime.of(2026, 11, 4, 10, 0))
                .status(AuctionStatus.ACTIVE)
                .build();
        sampleProduct.setStatus(ProductStatus.IN_AUCTION);

        Bid leadingBid = Bid.builder()
                .id(55L)
                .auctionId(3L)
                .bidderId(5L)
                .amount(new BigDecimal("63000000.00"))
                .status(BidStatus.LEADING)
                .build();

        when(auctionRepository.findByStatusAndEndTimeLessThanEqual(eq(AuctionStatus.ACTIVE), any()))
                .thenReturn(List.of(auction3));
        when(auctionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(auction3));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bidRepository.findTopByAuctionIdAndStatusOrderByAmountDesc(3L, BidStatus.LEADING))
                .thenReturn(Optional.of(leadingBid));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findByAuctionId(3L)).thenReturn(Optional.empty());

        auctionService.closeEndedAuctions();

        assertEquals(AuctionStatus.ENDED, auction3.getStatus());
        assertEquals(5L, auction3.getWinnerId());
        assertEquals(new BigDecimal("63000000.00"), auction3.getFinalPrice());
        assertNotNull(auction3.getClosedAt());
        assertEquals(BidStatus.WON, leadingBid.getStatus());
        assertEquals(ProductStatus.SOLD, sampleProduct.getStatus());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order createdOrder = orderCaptor.getValue();
        assertEquals(3L, createdOrder.getAuctionId());
        assertEquals(5L, createdOrder.getBuyerId());
        assertEquals(new BigDecimal("63000000.00"), createdOrder.getAmount());
        assertEquals(OrderStatus.PENDING_PAYMENT, createdOrder.getStatus());
        assertNotNull(createdOrder.getPaymentDeadline());
    }

    @Test
    void closeEndedAuctions_OrderFailureDoesNotRollbackAuctionEnded() {
        stubTransactionTemplate();
        sampleAuction.setId(3L);
        sampleAuction.setStatus(AuctionStatus.ACTIVE);
        sampleAuction.setEndTime(LocalDateTime.now().minusDays(5));
        sampleAuction.setBidCount(3);
        sampleAuction.setHighestBidderId(5L);
        sampleAuction.setCurrentPrice(new BigDecimal("63000000.00"));
        sampleAuction.setReservePrice(null);
        sampleProduct.setStatus(ProductStatus.IN_AUCTION);

        when(auctionRepository.findByStatusAndEndTimeLessThanEqual(eq(AuctionStatus.ACTIVE), any()))
                .thenReturn(List.of(sampleAuction));
        when(auctionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(sampleAuction));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bidRepository.findTopByAuctionIdAndStatusOrderByAmountDesc(3L, BidStatus.LEADING))
                .thenReturn(Optional.empty());
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(orderRepository.findByAuctionId(3L)).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenThrow(new RuntimeException("Simulated order constraint failure"));

        assertDoesNotThrow(() -> auctionService.closeEndedAuctions());
        assertEquals(AuctionStatus.ENDED, sampleAuction.getStatus());
        assertEquals(5L, sampleAuction.getWinnerId());
        assertEquals(ProductStatus.SOLD, sampleProduct.getStatus());
        verify(webSocketPublisher).publishAuctionUpdate(eq(3L), any(AuctionResponse.class));
    }

    @Test
    void closeEndedAuctions_OptimisticLockOnOne_ContinuesClosingOthers() {
        Auction secondAuction = Auction.builder()
                .id(101L)
                .productId(10L)
                .sellerId(1L)
                .startPrice(BigDecimal.valueOf(100))
                .bidIncrement(BigDecimal.valueOf(10))
                .currentPrice(BigDecimal.valueOf(100))
                .bidCount(0)
                .highestBidderId(null)
                .startTime(LocalDateTime.now().minusHours(2))
                .endTime(LocalDateTime.now().minusMinutes(5))
                .status(AuctionStatus.ACTIVE)
                .build();

        sampleAuction.setStatus(AuctionStatus.ACTIVE);
        sampleAuction.setEndTime(LocalDateTime.now().minusMinutes(5));

        when(auctionRepository.findByStatusAndEndTimeLessThanEqual(eq(AuctionStatus.ACTIVE), any()))
                .thenReturn(List.of(sampleAuction, secondAuction));

        when(transactionTemplate.execute(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Auction.class, 100L))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });

        when(auctionRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(secondAuction));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        assertDoesNotThrow(() -> auctionService.closeEndedAuctions());
        assertEquals(AuctionStatus.UNSOLD, secondAuction.getStatus());
    }
}
