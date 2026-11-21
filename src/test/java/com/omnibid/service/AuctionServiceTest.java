package com.omnibid.service;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.auction.AuctionUpdateRequest;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

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
    private AuctionCloser auctionCloser;
    @Mock
    private WebSocketPublisher webSocketPublisher;
    @Mock
    private NotificationService notificationService;

    private AppProperties appProperties;
    private AuctionServiceImpl auctionService;

    private User sampleSeller;
    private Product sampleProduct;
    private Auction sampleAuction;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        auctionService = new AuctionServiceImpl(
                auctionRepository,
                productRepository,
                productImageRepository,
                categoryRepository,
                userRepository,
                auctionCloser,
                webSocketPublisher,
                notificationService,
                null,
                appProperties,
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
                .startTime(NOW.plus(1, ChronoUnit.HOURS))
                .endTime(NOW.plus(5, ChronoUnit.HOURS))
                .status(AuctionStatus.SCHEDULED)
                .build();
    }

    @Test
    void createAuction_Success() {
        AuctionCreateRequest request = new AuctionCreateRequest(
                10L,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(200),
                NOW.plus(1, ChronoUnit.HOURS),
                NOW.plus(5, ChronoUnit.HOURS)
        );

        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));
        when(productImageRepository.countByProductId(10L)).thenReturn(1L);
        when(auctionRepository.saveAndFlush(any(Auction.class))).thenReturn(sampleAuction);
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        AuctionResponse response = auctionService.createAuction(1L, request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(AuctionStatus.SCHEDULED, response.getStatus());
        verify(productRepository).save(sampleProduct);
        assertEquals(ProductStatus.IN_AUCTION, sampleProduct.getStatus());
    }

    @Test
    void createAuction_NotOwner_ThrowsForbidden() {
        AuctionCreateRequest request = new AuctionCreateRequest(
                10L,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(10),
                null,
                NOW.plus(1, ChronoUnit.HOURS),
                NOW.plus(5, ChronoUnit.HOURS)
        );

        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));

        assertThatThrownBy(() -> auctionService.createAuction(999L, request))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.FORBIDDEN);
    }

    @Test
    void cancelAuction_Success_WhenNoBids() {
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(i -> i.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        AuctionResponse response = auctionService.cancelAuction(1L, 100L, "Cancelled by seller");

        assertNotNull(response);
        assertEquals(AuctionStatus.CANCELLED, response.getStatus());
        assertEquals(ProductStatus.AVAILABLE, sampleProduct.getStatus());
    }

    @Test
    @DisplayName("UT-CORE-03 Seller hủy phiên ACTIVE có Bid bị CANNOT_CANCEL")
    void cancelAuction_WithBids_ThrowsCannotCancel() {
        sampleAuction.setStatus(AuctionStatus.ACTIVE);
        sampleAuction.setBidCount(2);
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));

        assertThatThrownBy(() -> auctionService.cancelAuction(1L, 100L, "Try cancel"))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.CANNOT_CANCEL);
    }

    @Test
    @DisplayName("UT-CORE-05 Tạo phiên start_time quá khứ / độ dài < 1 giờ / > 30 ngày / giá sàn < giá khởi điểm -> 400 VALIDATION_ERROR")
    void createAuction_InvalidTimesOrPrices_ThrowsValidationError() {
        // 1. start_time in past
        AuctionCreateRequest pastRequest = new AuctionCreateRequest(
                10L,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(10),
                null,
                NOW.minus(1, ChronoUnit.HOURS),
                NOW.plus(2, ChronoUnit.HOURS)
        );
        assertThatThrownBy(() -> auctionService.createAuction(1L, pastRequest))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.VALIDATION_ERROR);

        // 2. duration < 1 hour
        AuctionCreateRequest shortDuration = new AuctionCreateRequest(
                10L,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(10),
                null,
                NOW.plus(1, ChronoUnit.HOURS),
                NOW.plus(1, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES)
        );
        assertThatThrownBy(() -> auctionService.createAuction(1L, shortDuration))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.VALIDATION_ERROR);

        // 3. duration > 30 days
        AuctionCreateRequest longDuration = new AuctionCreateRequest(
                10L,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(10),
                null,
                NOW.plus(1, ChronoUnit.HOURS),
                NOW.plus(1, ChronoUnit.HOURS).plus(31, ChronoUnit.DAYS)
        );
        assertThatThrownBy(() -> auctionService.createAuction(1L, longDuration))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.VALIDATION_ERROR);

        // 4. reserve_price < start_price
        AuctionCreateRequest lowReserve = new AuctionCreateRequest(
                10L,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(50),
                NOW.plus(1, ChronoUnit.HOURS),
                NOW.plus(5, ChronoUnit.HOURS)
        );
        assertThatThrownBy(() -> auctionService.createAuction(1L, lowReserve))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void createAuction_NoImages_ThrowsBadRequest() {
        AuctionCreateRequest request = new AuctionCreateRequest(
                10L,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(10),
                null,
                NOW.plus(1, ChronoUnit.HOURS),
                NOW.plus(5, ChronoUnit.HOURS)
        );

        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));
        when(productImageRepository.countByProductId(10L)).thenReturn(0L);

        assertThatThrownBy(() -> auctionService.createAuction(1L, request))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.PRODUCT_NOT_AVAILABLE);
    }

    @Test
    void updateAuction_Success_WhenScheduled() {
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(i -> i.getArgument(0));

        AuctionUpdateRequest updateRequest = new AuctionUpdateRequest(
                BigDecimal.valueOf(150),
                BigDecimal.valueOf(15),
                BigDecimal.valueOf(250),
                NOW.plus(2, ChronoUnit.HOURS),
                NOW.plus(6, ChronoUnit.HOURS)
        );

        AuctionResponse response = auctionService.updateAuction(1L, 100L, updateRequest);
        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(150), sampleAuction.getStartPrice());
        assertEquals(BigDecimal.valueOf(15), sampleAuction.getBidIncrement());
    }

    @Test
    void updateAuction_ActiveWithBids_ThrowsConflict() {
        sampleAuction.setStatus(AuctionStatus.ACTIVE);
        sampleAuction.setBidCount(1);
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));

        AuctionUpdateRequest updateRequest = new AuctionUpdateRequest(
                BigDecimal.valueOf(150), null, null, null, null
        );

        assertThatThrownBy(() -> auctionService.updateAuction(1L, 100L, updateRequest))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getErrorCode() == ErrorCode.AUCTION_NOT_EDITABLE);
    }
}
