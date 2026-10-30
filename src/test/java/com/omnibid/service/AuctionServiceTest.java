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
import com.omnibid.repository.*;
import com.omnibid.service.impl.AuctionServiceImpl;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private WebSocketPublisher webSocketPublisher;

    @InjectMocks
    private AuctionServiceImpl auctionService;

    private User sampleSeller;
    private Product sampleProduct;
    private Auction sampleAuction;

    @BeforeEach
    void setUp() {
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
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(5))
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
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(5))
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
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(5))
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        AppException ex = assertThrows(AppException.class, () -> auctionService.createAuction(999L, request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
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
        verify(webSocketPublisher).publishAuctionUpdate(eq(100L), any());
    }

    @Test
    void cancelAuction_WithBids_ThrowsBadRequest() {
        sampleAuction.setBidCount(2);
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(sampleAuction));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleSeller));

        AppException ex = assertThrows(AppException.class, () -> auctionService.cancelAuction(1L, 100L, "Try cancel"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }
}

