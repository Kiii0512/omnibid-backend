package com.omnibid.service;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionDetailResponse;
import com.omnibid.dto.auction.AuctionUpdateRequest;
import com.omnibid.dto.websocket.AuctionEvent;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionEventType;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.mapper.AuctionMapper;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.CategoryRepository;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.impl.AuctionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");
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
    private OrderRepository orderRepository;
    @Mock
    private WebSocketPublisher webSocketPublisher;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuctionCloser auctionCloser;
    @Mock
    private AuctionLifecycleSupport auctionLifecycleSupport;

    private AppProperties appProperties;
    private AuctionMapper auctionMapper;
    private AuctionServiceImpl auctionService;

    private User sampleSeller;
    private Product sampleProduct;
    private Auction sampleAuction;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        auctionMapper = new AuctionMapper(
                productRepository,
                productImageRepository,
                categoryRepository,
                userRepository,
                orderRepository
        );

        auctionService = new AuctionServiceImpl(
                auctionRepository,
                productRepository,
                productImageRepository,
                webSocketPublisher,
                auctionCloser,
                auctionLifecycleSupport,
                auctionMapper,
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
                .categoryId(5L)
                .title("Vintage Watch")
                .status(ProductStatus.AVAILABLE)
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
                .startTime(NOW.plus(1, ChronoUnit.HOURS))
                .endTime(NOW.plus(5, ChronoUnit.HOURS))
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
                .startTime(NOW.plus(1, ChronoUnit.HOURS))
                .endTime(NOW.plus(5, ChronoUnit.HOURS))
                .build();

        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));
        when(productImageRepository.countByProductId(10L)).thenReturn(2L);
        when(auctionRepository.findFirstByProductIdAndStatusIn(eq(10L), any())).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);
        when(auctionRepository.saveAndFlush(any(Auction.class))).thenReturn(sampleAuction);
        when(userRepository.findAllById(any())).thenReturn(List.of(sampleSeller));
        when(productImageRepository.findByProductIdOrderByIdAsc(10L)).thenReturn(List.of());

        AuctionDetailResponse response = auctionService.createAuction(1L, request);

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("Vintage Watch", response.title());
        assertEquals(AuctionStatus.SCHEDULED, response.status());
        assertTrue(response.viewerIsSeller());
        assertEquals(BigDecimal.valueOf(200), response.reservePrice());
        assertEquals(ProductStatus.IN_AUCTION, sampleProduct.getStatus());
    }

    @Test
    void createAuction_NotOwner_ThrowsForbidden() {
        AuctionCreateRequest request = AuctionCreateRequest.builder()
                .productId(10L)
                .startPrice(BigDecimal.valueOf(100))
                .bidIncrement(BigDecimal.valueOf(10))
                .startTime(NOW.plus(1, ChronoUnit.HOURS))
                .endTime(NOW.plus(5, ChronoUnit.HOURS))
                .build();

        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));

        BusinessException ex = assertThrows(BusinessException.class, () -> auctionService.createAuction(999L, request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals(ErrorCode.FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("Tạo phiên khi sản phẩm chưa có ảnh bị 409 PRODUCT_NOT_AVAILABLE")
    void createAuction_NoImages_ThrowsProductNotAvailable() {
        AuctionCreateRequest request = AuctionCreateRequest.builder()
                .productId(10L)
                .startPrice(BigDecimal.valueOf(100))
                .bidIncrement(BigDecimal.valueOf(10))
                .startTime(NOW.plus(1, ChronoUnit.HOURS))
                .endTime(NOW.plus(5, ChronoUnit.HOURS))
                .build();

        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleProduct));
        when(productImageRepository.countByProductId(10L)).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> auctionService.createAuction(1L, request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(ErrorCode.PRODUCT_NOT_AVAILABLE, ex.getCode());
    }

    @Test
    @DisplayName("Tạo phiên sai thời gian / giá sàn < giá khởi điểm -> 400 VALIDATION_ERROR")
    void createAuction_InvalidTimesOrPrices_ThrowsValidationError() {
        AuctionCreateRequest pastRequest = AuctionCreateRequest.builder()
                .productId(10L)
                .startPrice(BigDecimal.valueOf(100))
                .bidIncrement(BigDecimal.valueOf(10))
                .startTime(NOW) // must be strictly after now
                .endTime(NOW.plus(2, ChronoUnit.HOURS))
                .build();
        BusinessException ex1 = assertThrows(BusinessException.class, () -> auctionService.createAuction(1L, pastRequest));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex1.getCode());

        AuctionCreateRequest shortDuration = AuctionCreateRequest.builder()
                .productId(10L)
                .startPrice(BigDecimal.valueOf(100))
                .bidIncrement(BigDecimal.valueOf(10))
                .startTime(NOW.plus(1, ChronoUnit.HOURS))
                .endTime(NOW.plus(90, ChronoUnit.MINUTES))
                .build();
        BusinessException ex2 = assertThrows(BusinessException.class, () -> auctionService.createAuction(1L, shortDuration));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex2.getCode());

        AuctionCreateRequest longDuration = AuctionCreateRequest.builder()
                .productId(10L)
                .startPrice(BigDecimal.valueOf(100))
                .bidIncrement(BigDecimal.valueOf(10))
                .startTime(NOW.plus(1, ChronoUnit.HOURS))
                .endTime(NOW.plus(1, ChronoUnit.HOURS).plus(31, ChronoUnit.DAYS))
                .build();
        BusinessException ex3 = assertThrows(BusinessException.class, () -> auctionService.createAuction(1L, longDuration));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex3.getCode());

        AuctionCreateRequest lowReserve = AuctionCreateRequest.builder()
                .productId(10L)
                .startPrice(BigDecimal.valueOf(100))
                .bidIncrement(BigDecimal.valueOf(10))
                .reservePrice(BigDecimal.valueOf(50))
                .startTime(NOW.plus(1, ChronoUnit.HOURS))
                .endTime(NOW.plus(5, ChronoUnit.HOURS))
                .build();
        BusinessException ex4 = assertThrows(BusinessException.class, () -> auctionService.createAuction(1L, lowReserve));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex4.getCode());
    }

    @Test
    @DisplayName("BR-07: Sửa phiên SCHEDULED thành công và phát WebSocket AUCTION_UPDATED")
    void updateAuction_Success_WhenScheduled() {
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(i -> i.getArgument(0));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(userRepository.findAllById(any())).thenReturn(List.of(sampleSeller));
        when(productImageRepository.findByProductIdOrderByIdAsc(10L)).thenReturn(List.of());

        AuctionUpdateRequest updateRequest = new AuctionUpdateRequest(
                BigDecimal.valueOf(150),
                BigDecimal.valueOf(15),
                BigDecimal.valueOf(250),
                NOW.plus(2, ChronoUnit.HOURS),
                NOW.plus(6, ChronoUnit.HOURS)
        );

        AuctionDetailResponse response = auctionService.updateAuction(1L, 100L, updateRequest);

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(150), sampleAuction.getStartPrice());
        assertEquals(BigDecimal.valueOf(150), sampleAuction.getCurrentPrice());
        assertEquals(BigDecimal.valueOf(15), sampleAuction.getBidIncrement());

        ArgumentCaptor<AuctionEvent> eventCaptor = ArgumentCaptor.forClass(AuctionEvent.class);
        verify(webSocketPublisher).publishAuctionEvent(eventCaptor.capture());
        assertEquals(AuctionEventType.AUCTION_UPDATED, eventCaptor.getValue().type());
    }

    @Test
    @DisplayName("BR-07: Phiên ACTIVE dù chưa có Bid vẫn không sửa được -> 409 AUCTION_NOT_EDITABLE")
    void updateAuction_ActiveNoBids_ThrowsAuctionNotEditable() {
        sampleAuction.setStatus(AuctionStatus.ACTIVE);
        sampleAuction.setBidCount(0);
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));

        AuctionUpdateRequest updateRequest = new AuctionUpdateRequest(
                BigDecimal.valueOf(150), null, null, null, null
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> auctionService.updateAuction(1L, 100L, updateRequest));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(ErrorCode.AUCTION_NOT_EDITABLE, ex.getCode());
    }

    @Test
    @DisplayName("BR-08: Seller hủy phiên ACTIVE có Bid bị 409 CANNOT_CANCEL")
    void cancelAuction_ActiveWithBids_ThrowsCannotCancel() {
        sampleAuction.setStatus(AuctionStatus.ACTIVE);
        sampleAuction.setBidCount(2);
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> auctionService.cancelAuction(1L, 100L, "Try cancel"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(ErrorCode.CANNOT_CANCEL, ex.getCode());
        verifyNoInteractions(auctionLifecycleSupport);
    }

    @Test
    @DisplayName("BR-08: Seller hủy phiên đã ENDED bị 409 INVALID_STATUS_TRANSITION")
    void cancelAuction_AlreadyEnded_ThrowsInvalidStatusTransition() {
        sampleAuction.setStatus(AuctionStatus.ENDED);
        when(auctionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleAuction));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> auctionService.cancelAuction(1L, 100L, "Too late"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.getCode());
    }

    @Test
    @DisplayName("BR-16: Hủy phiên khi Product HIDDEN thì Product giữ HIDDEN và gửi AUCTION_CANCELLED")
    void cancelLocked_WhenProductHidden_ProductKeepsHidden() {
        Product hiddenProduct = Product.builder()
                .id(10L)
                .sellerId(1L)
                .title("Hidden Product")
                .status(ProductStatus.HIDDEN)
                .build();

        AuctionLifecycleSupport support = new AuctionLifecycleSupport(
                auctionRepository,
                productRepository,
                userRepository,
                webSocketPublisher,
                notificationService,
                clock
        );

        when(auctionRepository.save(any(Auction.class))).thenAnswer(i -> i.getArgument(0));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(hiddenProduct));

        Auction cancelled = support.cancelLocked(sampleAuction, "Cancelled");

        assertEquals(AuctionStatus.CANCELLED, cancelled.getStatus());
        assertEquals(ProductStatus.HIDDEN, hiddenProduct.getStatus());
        verify(productRepository, never()).save(any(Product.class));
        verify(notificationService).notify(eq(1L), eq(NotificationType.AUCTION_CANCELLED), any(), any(), eq("AUCTION"), eq(100L));
        verify(webSocketPublisher).publishAuctionEvent(argThat(ev -> ev.type() == AuctionEventType.AUCTION_CANCELLED));
    }

    @Test
    @DisplayName("Xem chi tiết phiên của sản phẩm HIDDEN bởi Guest/USER bị 404 NOT_FOUND")
    void getAuctionById_HiddenProductNonAdmin_ThrowsNotFound() {
        sampleProduct.setStatus(ProductStatus.HIDDEN);
        when(auctionRepository.findById(100L)).thenReturn(Optional.of(sampleAuction));
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> auctionService.getAuctionById(100L, new CurrentUser(1L, false)));
        assertEquals(ErrorCode.NOT_FOUND, ex.getCode());
    }
}
