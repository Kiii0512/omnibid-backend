package com.omnibid.service;

import com.omnibid.common.AppException;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.impl.AuctionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WebSocketPublisher webSocketPublisher;

    @Mock
    private AuctionCloser auctionCloser;

    @Mock
    private AuctionLifecycleSupport auctionLifecycleSupport;

    private AuctionServiceImpl auctionService;

    private User sampleSeller;
    private Product sampleProduct;
    private Auction sampleAuction;

    @BeforeEach
    void setUp() {
        auctionService = new AuctionServiceImpl(
                auctionRepository,
                productRepository,
                userRepository,
                webSocketPublisher,
                auctionCloser,
                auctionLifecycleSupport,
                clock
        );

        sampleSeller = User.builder()
                .id(1L)
                .fullName("Seller Name")
                .email("seller@example.com")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        sampleProduct = Product.builder()
                .id(10L)
                .sellerId(1L)
                .title("Vintage Watch")
                .status(ProductStatus.AVAILABLE)
                .build();

        sampleAuction = Auction.builder()
                .id(100L)
                .productId(10L)
                .sellerId(1L)
                .startPrice(BigDecimal.valueOf(100))
                .bidIncrement(BigDecimal.valueOf(10))
                .currentPrice(BigDecimal.valueOf(100))
                .bidCount(0)
                .startTime(NOW.plusHours(1))
                .endTime(NOW.plusHours(5))
                .status(AuctionStatus.SCHEDULED)
                .build();
    }

    @Test
    void createAuction_Success() {
        AuctionCreateRequest request = AuctionCreateRequest.builder()
                .productId(10L)
                .startPrice(BigDecimal.valueOf(100))
                .bidIncrement(BigDecimal.valueOf(10))
                .reservePrice(BigDecimal.valueOf(200))
                .startTime(NOW.plusHours(1))
                .endTime(NOW.plusHours(5))
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(auctionRepository.save(any(Auction.class))).thenReturn(sampleAuction);
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleSeller));

        AuctionResponse response = auctionService.createAuction(1L, request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Vintage Watch", response.getProductTitle());
        assertEquals(AuctionStatus.SCHEDULED, response.getStatus());
        verify(productRepository).save(sampleProduct);
        assertEquals(ProductStatus.IN_AUCTION, sampleProduct.getStatus());
    }

    @Test
    void createAuction_NotOwner_ThrowsForbidden() {
        AuctionCreateRequest request = AuctionCreateRequest.builder()
                .productId(10L)
                .startPrice(BigDecimal.valueOf(100))
                .bidIncrement(BigDecimal.valueOf(10))
                .startTime(NOW.plusHours(1))
                .endTime(NOW.plusHours(5))
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        AppException ex = assertThrows(AppException.class, () -> auctionService.createAuction(999L, request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void cancelAuction_Success_WhenNoBids() {
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleSeller));

        Auction cancelledAuction = Auction.builder()
                .id(100L)
                .productId(10L)
                .sellerId(1L)
                .status(AuctionStatus.CANCELLED)
                .closedAt(NOW)
                .cancelReason("Cancelled by seller")
                .build();
        when(auctionLifecycleSupport.cancelLocked(sampleAuction, "Cancelled by seller")).thenReturn(cancelledAuction);
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        AuctionResponse response = auctionService.cancelAuction(1L, 100L, "Cancelled by seller");

        assertNotNull(response);
        assertEquals(AuctionStatus.CANCELLED, response.getStatus());
        verify(auctionLifecycleSupport).cancelLocked(sampleAuction, "Cancelled by seller");
    }

    @Test
    @DisplayName("UT-CORE-03 Seller hủy phiên ACTIVE có Bid bị 409 CANNOT_CANCEL")
    void cancelAuction_WithBids_ThrowsBadRequest() {
        sampleAuction.setStatus(AuctionStatus.ACTIVE);
        sampleAuction.setBidCount(2);
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleSeller));

        AppException ex = assertThrows(AppException.class, () -> auctionService.cancelAuction(1L, 100L, "Try cancel"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("CANNOT_CANCEL", ex.getCode());
        verifyNoInteractions(auctionLifecycleSupport);
    }

    @Test
    @DisplayName("UT-CORE-04 Seller hủy khi Product HIDDEN thì Product giữ HIDDEN")
    void cancelAuction_WhenProductHidden_ProductKeepsHidden() {
        Product hiddenProduct = Product.builder()
                .id(10L)
                .sellerId(1L)
                .title("Hidden Product")
                .status(ProductStatus.HIDDEN)
                .build();

        AuctionLifecycleSupport support = new AuctionLifecycleSupport(
                auctionRepository,
                mock(com.omnibid.repository.BidRepository.class),
                productRepository,
                webSocketPublisher,
                clock
        );

        when(auctionRepository.save(any(Auction.class))).thenAnswer(i -> i.getArgument(0));
        when(productRepository.findById(10L)).thenReturn(Optional.of(hiddenProduct));

        Auction cancelled = support.cancelLocked(sampleAuction, "Cancelled");
        assertEquals(AuctionStatus.CANCELLED, cancelled.getStatus());
        assertEquals(ProductStatus.HIDDEN, hiddenProduct.getStatus());
        verify(productRepository, never()).save(any(Product.class));
    }
}
