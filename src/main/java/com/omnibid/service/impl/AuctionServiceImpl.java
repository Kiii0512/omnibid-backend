package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.MaskingUtils;
import com.omnibid.common.PageResponse;
import com.omnibid.common.SortWhitelist;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionDetailResponse;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.auction.AuctionUpdateRequest;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.dto.websocket.AuctionEvent;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Category;
import com.omnibid.entity.Product;
import com.omnibid.entity.ProductImage;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionEventType;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.ReserveStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.CategoryRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.repository.specification.AuctionSpecification;
import com.omnibid.service.AuctionCloser;
import com.omnibid.service.AuctionService;
import com.omnibid.service.NotificationService;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionServiceImpl implements AuctionService {

    private final AuctionRepository auctionRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AuctionCloser auctionCloser;
    private final WebSocketPublisher webSocketPublisher;
    private final NotificationService notificationService;
    private final com.omnibid.common.AfterCommitExecutor afterCommitExecutor;
    private final AppProperties appProperties;
    private final Clock clock;

    private static final Set<String> ALLOWED_SORTS = Set.of("endTime", "createdAt", "currentPrice");

    @Override
    @Transactional
    public AuctionResponse createAuction(Long sellerId, AuctionCreateRequest request) {
        Instant now = Instant.now(clock);
        if (!request.startTime().isAfter(now)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thời gian bắt đầu phải ở tương lai");
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thời gian kết thúc phải sau thời gian bắt đầu");
        }

        Duration duration = Duration.between(request.startTime(), request.endTime());
        Duration minDuration = appProperties.getAuction().getMinDuration();
        Duration maxDuration = appProperties.getAuction().getMaxDuration();
        if (duration.compareTo(minDuration) < 0 || duration.compareTo(maxDuration) > 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Độ dài phiên đấu giá phải từ " + minDuration.toHours() + " giờ đến " + maxDuration.toDays() + " ngày"
            );
        }

        if (request.reservePrice() != null && request.reservePrice().compareTo(request.startPrice()) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Giá sàn phải lớn hơn hoặc bằng giá khởi điểm");
        }

        Product product = productRepository.findByIdForUpdate(request.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        if (!product.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền tạo phiên cho sản phẩm này");
        }
        if (product.getStatus() != ProductStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE, "Sản phẩm không ở trạng thái sẵn sàng để tạo phiên");
        }
        long imgCount = productImageRepository.countByProductId(request.productId());
        if (imgCount < 1 || imgCount > 5) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE, "Sản phẩm phải có từ 1 đến 5 ảnh");
        }
        if (auctionRepository.findFirstByProductIdAndStatusIn(request.productId(), List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE)).isPresent()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE, "Sản phẩm đang có phiên đấu giá mở");
        }

        Auction auction = Auction.builder()
                .productId(request.productId())
                .sellerId(sellerId)
                .startPrice(request.startPrice())
                .bidIncrement(request.bidIncrement())
                .reservePrice(request.reservePrice())
                .currentPrice(request.startPrice())
                .highestBidderId(null)
                .bidCount(0)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(AuctionStatus.SCHEDULED)
                .build();

        try {
            auction = auctionRepository.saveAndFlush(auction);
            product.markInAuction();
            productRepository.save(product);
        } catch (DataIntegrityViolationException dive) {
            log.warn("Active auction constraint triggered: productId={}", request.productId());
            throw new BusinessException(ErrorCode.CONFLICT, "Sản phẩm đã có phiên đấu giá mở");
        }

        return mapToResponse(auction);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuctionResponse> searchAuctions(
            String q, Long categoryId, AuctionStatus status, BigDecimal minPrice,
            BigDecimal maxPrice, Instant endBefore, Pageable pageable
    ) {
        Pageable effectivePageable = resolvePageable(pageable, Sort.by(Sort.Direction.ASC, "endTime"), ALLOWED_SORTS);
        Specification<Auction> spec = AuctionSpecification.filter(q, categoryId, status, minPrice, maxPrice, null, endBefore);
        Page<Auction> page = auctionRepository.findAll(spec, effectivePageable);
        return PageResponse.from(page, this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AuctionDetailResponse getAuctionById(Long id, Long currentUserId) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Phiên đấu giá không tồn tại"));

        Product product = productRepository.findById(auction.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        if (product.getStatus() == ProductStatus.HIDDEN) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Phiên đấu giá không tồn tại");
        }

        return mapToDetailResponse(auction, product, currentUserId);
    }

    @Override
    @Transactional
    public AuctionResponse updateAuction(Long sellerId, Long id, AuctionUpdateRequest request) {
        Auction auction = auctionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Phiên đấu giá không tồn tại"));

        if (!auction.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền chỉnh sửa phiên này");
        }
        if (auction.getStatus() != AuctionStatus.SCHEDULED) {
            throw new BusinessException(ErrorCode.AUCTION_NOT_EDITABLE, "Chỉ có thể sửa phiên khi còn ở trạng thái SCHEDULED");
        }

        Instant now = Instant.now(clock);
        if (request.startTime() != null) {
            if (!request.startTime().isAfter(now)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thời gian bắt đầu phải ở tương lai");
            }
            auction.setStartTime(request.startTime());
        }
        if (request.endTime() != null) {
            if (!request.endTime().isAfter(auction.getStartTime())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thời gian kết thúc phải sau thời gian bắt đầu");
            }
            auction.setEndTime(request.endTime());
        }

        Duration duration = Duration.between(auction.getStartTime(), auction.getEndTime());
        Duration minDuration = appProperties.getAuction().getMinDuration();
        Duration maxDuration = appProperties.getAuction().getMaxDuration();
        if (duration.compareTo(minDuration) < 0 || duration.compareTo(maxDuration) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Độ dài phiên đấu giá không hợp lệ");
        }

        if (request.startPrice() != null) {
            auction.setStartPrice(request.startPrice());
            if (auction.getBidCount() == null || auction.getBidCount() == 0) {
                auction.setCurrentPrice(request.startPrice());
            }
        }
        if (request.bidIncrement() != null) {
            auction.setBidIncrement(request.bidIncrement());
        }
        if (request.reservePrice() != null) {
            if (request.reservePrice().compareTo(auction.getStartPrice()) < 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Giá sàn phải lớn hơn hoặc bằng giá khởi điểm");
            }
            auction.setReservePrice(request.reservePrice());
        }

        Auction updated = auctionRepository.save(auction);
        publishAuctionEvent(updated, AuctionEventType.AUCTION_UPDATED);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public AuctionResponse cancelAuction(Long sellerId, Long id, String reason) {
        Auction auction = auctionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Phiên đấu giá không tồn tại"));

        if (!auction.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền hủy phiên này");
        }

        if (auction.getStatus() == AuctionStatus.ACTIVE && auction.getBidCount() != null && auction.getBidCount() > 0) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL, "Người bán không thể hủy phiên ACTIVE đã có lượt đặt giá");
        }
        if (auction.getStatus() != AuctionStatus.SCHEDULED && auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Không thể hủy phiên ở trạng thái " + auction.getStatus());
        }

        Product product = productRepository.findByIdForUpdate(auction.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        auction.cancel(reason);
        auction.setClosedAt(Instant.now(clock));
        auctionRepository.save(auction);

        product.release();
        productRepository.save(product);

        publishAuctionEvent(auction, AuctionEventType.AUCTION_CANCELLED);

        Long seller = auction.getSellerId();
        Long leader = auction.getHighestBidderId();
        Long aId = auction.getId();
        String rsn = reason;
        if (afterCommitExecutor != null && notificationService != null) {
            afterCommitExecutor.execute(() -> {
                notificationService.notify(
                        seller,
                        com.omnibid.enums.NotificationType.AUCTION_CANCELLED,
                        com.omnibid.common.NotificationTemplates.auctionCancelledTitle(),
                        com.omnibid.common.NotificationTemplates.auctionCancelledContent(aId, rsn),
                        "AUCTION",
                        aId
                );
                if (leader != null && !leader.equals(seller)) {
                    notificationService.notify(
                            leader,
                            com.omnibid.enums.NotificationType.AUCTION_CANCELLED,
                            com.omnibid.common.NotificationTemplates.auctionCancelledTitle(),
                            com.omnibid.common.NotificationTemplates.auctionCancelledContent(aId, rsn),
                            "AUCTION",
                            aId
                    );
                }
            });
        }
        return mapToResponse(auction);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuctionResponse> getMyAuctions(Long sellerId, String keyword, AuctionStatus status, Pageable pageable) {
        Page<Auction> page = (status != null)
                ? auctionRepository.findBySellerIdAndStatus(sellerId, status, pageable)
                : auctionRepository.findBySellerId(sellerId, pageable);
        return PageResponse.from(page, this::mapToResponse);
    }

    @Override
    public void startScheduledAuctions() {
        auctionCloser.startOne(null);
    }

    @Override
    public void closeEndedAuctions() {
        auctionCloser.closeOne(null);
    }

    private Pageable resolvePageable(Pageable pageable, Sort defaultSort, Set<String> allowedSorts) {
        if (pageable.getSort().isSorted()) {
            SortWhitelist.validate(pageable, allowedSorts);
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    }

    private AuctionResponse mapToResponse(Auction a) {
        Product p = productRepository.findById(a.getProductId()).orElse(null);
        String title = p != null ? p.getTitle() : null;
        Long catId = p != null ? p.getCategoryId() : null;
        String catName = catId != null ? categoryRepository.findById(catId).map(Category::getName).orElse(null) : null;
        String sellerName = userRepository.findById(a.getSellerId()).map(User::getFullName).orElse(null);
        String primaryImg = productImageRepository.findByProductIdAndIsPrimaryTrue(a.getProductId())
                .map(ProductImage::getImageUrl).orElse(null);

        return AuctionResponse.builder()
                .id(a.getId()).productId(a.getProductId()).productTitle(title).categoryId(catId)
                .categoryName(catName).sellerId(a.getSellerId()).sellerName(sellerName)
                .primaryImageUrl(primaryImg).startPrice(a.getStartPrice()).bidIncrement(a.getBidIncrement())
                .currentPrice(a.getCurrentPrice()).minNextBid(a.minNextBid()).reserveStatus(a.reserveStatus().name())
                .bidCount(a.getBidCount()).startTime(a.getStartTime()).endTime(a.getEndTime()).status(a.getStatus())
                .winnerId(a.getWinnerId()).finalPrice(a.getFinalPrice()).closedAt(a.getClosedAt())
                .cancelReason(a.getCancelReason()).createdAt(a.getCreatedAt()).build();
    }

    private AuctionDetailResponse mapToDetailResponse(Auction a, Product p, Long currentUserId) {
        String catName = categoryRepository.findById(p.getCategoryId()).map(Category::getName).orElse(null);
        String sellerName = userRepository.findById(a.getSellerId()).map(User::getFullName).orElse(null);
        String leaderName = a.getHighestBidderId() != null
                ? userRepository.findById(a.getHighestBidderId()).map(u -> MaskingUtils.maskName(u.getFullName())).orElse(null)
                : null;
        String primaryImg = productImageRepository.findByProductIdAndIsPrimaryTrue(p.getId())
                .map(ProductImage::getImageUrl).orElse(null);
        List<ProductImageResponse> images = productImageRepository.findByProductIdOrderByIdAsc(p.getId()).stream()
                .map(img -> new ProductImageResponse(img.getId(), img.getImageUrl(), img.getPublicId(), img.getIsPrimary(), img.getCreatedAt()))
                .toList();

        boolean canSeeReserve = currentUserId != null && currentUserId.equals(a.getSellerId());
        BigDecimal reservePrice = canSeeReserve ? a.getReservePrice() : null;

        return AuctionDetailResponse.builder()
                .id(a.getId()).productId(p.getId()).title(p.getTitle()).description(p.getDescription())
                .categoryId(p.getCategoryId()).categoryName(catName).condition(p.getCondition())
                .sellerId(a.getSellerId()).sellerName(sellerName).startPrice(a.getStartPrice())
                .bidIncrement(a.getBidIncrement()).currentPrice(a.getCurrentPrice()).minNextBid(a.minNextBid())
                .reservePrice(reservePrice).reserveStatus(a.reserveStatus()).highestBidderName(leaderName)
                .bidCount(a.getBidCount()).startTime(a.getStartTime()).endTime(a.getEndTime()).status(a.getStatus())
                .winnerId(a.getWinnerId()).finalPrice(a.getFinalPrice()).closedAt(a.getClosedAt())
                .cancelReason(a.getCancelReason()).primaryImageUrl(primaryImg).images(images).createdAt(a.getCreatedAt()).build();
    }

    private void publishAuctionEvent(Auction a, AuctionEventType type) {
        AuctionEvent event = AuctionEvent.builder()
                .type(type)
                .auctionId(a.getId())
                .status(a.getStatus())
                .currentPrice(a.getCurrentPrice())
                .minNextBid(a.minNextBid())
                .bidCount(a.getBidCount())
                .reserveStatus(a.reserveStatus())
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .createdAt(Instant.now(clock))
                .build();
        webSocketPublisher.publishAuctionEvent(event);
    }
}
