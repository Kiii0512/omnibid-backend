package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.PageResponse;
import com.omnibid.common.SortWhitelist;
import com.omnibid.config.AppProperties;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionDetailResponse;
import com.omnibid.dto.auction.AuctionSummaryResponse;
import com.omnibid.dto.auction.AuctionUpdateRequest;
import com.omnibid.dto.auction.MyAuctionItemResponse;
import com.omnibid.dto.websocket.AuctionEvent;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Product;
import com.omnibid.enums.AuctionEventType;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.mapper.AuctionMapper;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.specification.AuctionSpecification;
import com.omnibid.security.CurrentUser;
import com.omnibid.service.AuctionCloser;
import com.omnibid.service.AuctionLifecycleSupport;
import com.omnibid.service.AuctionService;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionServiceImpl implements AuctionService {

    private static final List<AuctionStatus> OPEN_STATUSES = List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE);

    private static final Set<String> PUBLIC_AUCTION_SORTS = Set.of(
            "endTime,asc",
            "createdAt,desc",
            "currentPrice,asc",
            "currentPrice,desc"
    );

    private static final Set<String> MY_AUCTION_SORT_FIELDS = Set.of(
            "id", "createdAt", "startTime", "endTime", "currentPrice", "status"
    );

    private final AuctionRepository auctionRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final AuctionCloser auctionCloser;
    private final AuctionLifecycleSupport auctionLifecycleSupport;
    private final AuctionMapper auctionMapper;
    private final AppProperties appProperties;
    private final Clock clock;

    @Override
    @Transactional
    public AuctionDetailResponse createAuction(Long sellerId, AuctionCreateRequest request) {
        Instant now = Instant.now(clock);
        validateScheduleAndPrices(
                request.getStartPrice(),
                request.getBidIncrement(),
                request.getReservePrice(),
                request.getStartTime(),
                request.getEndTime(),
                now
        );

        Product product = productRepository.findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm"));

        if (!product.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không sở hữu sản phẩm này");
        }

        if (product.getStatus() != ProductStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE, "Sản phẩm không ở trạng thái sẵn sàng để đấu giá");
        }

        long imageCount = productImageRepository.countByProductId(product.getId());
        if (imageCount < 1 || imageCount > 5) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE, "Sản phẩm phải có từ 1 đến 5 ảnh trước khi tạo phiên");
        }

        if (auctionRepository.findFirstByProductIdAndStatusIn(product.getId(), OPEN_STATUSES).isPresent()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE, "Sản phẩm đang có phiên đấu giá mở");
        }

        product.markInAuction();
        productRepository.save(product);

        Auction auction = Auction.builder()
                .productId(product.getId())
                .sellerId(sellerId)
                .startPrice(request.getStartPrice())
                .bidIncrement(request.getBidIncrement())
                .reservePrice(request.getReservePrice())
                .currentPrice(request.getStartPrice())
                .highestBidderId(null)
                .bidCount(0)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(AuctionStatus.SCHEDULED)
                .build();

        Auction savedAuction;
        try {
            savedAuction = auctionRepository.saveAndFlush(auction);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate open auction constraint triggered for productId={}", product.getId());
            throw new BusinessException(ErrorCode.CONFLICT, "Sản phẩm đã có phiên đấu giá đang mở");
        }

        log.info("Created scheduled auction id={} for productId={} by sellerId={}",
                savedAuction.getId(), product.getId(), sellerId);

        return auctionMapper.toDetail(savedAuction, product, sellerId);
    }

    @Override
    @Transactional(readOnly = true)
    public AuctionDetailResponse getAuctionById(Long id, CurrentUser currentUser) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên đấu giá"));

        Product product = productRepository.findById(auction.getProductId()).orElse(null);
        boolean isAdmin = currentUser != null && currentUser.isAdmin();
        if (!isAdmin && (product == null || product.getStatus() == ProductStatus.HIDDEN)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên đấu giá");
        }

        Long currentUserId = currentUser != null ? currentUser.id() : null;
        return auctionMapper.toDetail(auction, product, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuctionSummaryResponse> searchAuctions(
            String keyword,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            AuctionStatus status,
            Instant endBefore,
            Pageable pageable
    ) {
        SortWhitelist.validateDirections(pageable, PUBLIC_AUCTION_SORTS);

        Specification<Auction> spec = AuctionSpecification.publicFilter(
                keyword, categoryId, minPrice, maxPrice, status, endBefore
        );
        Page<Auction> page = auctionRepository.findAll(spec, pageable);
        return auctionMapper.toSummaryPage(page);
    }

    @Override
    @Transactional
    public AuctionDetailResponse updateAuction(Long sellerId, Long auctionId, AuctionUpdateRequest request) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên đấu giá"));

        if (!auction.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không sở hữu phiên đấu giá này");
        }

        // BR-07: chỉ được sửa khi phiên còn SCHEDULED
        if (auction.getStatus() != AuctionStatus.SCHEDULED) {
            throw new BusinessException(ErrorCode.AUCTION_NOT_EDITABLE, "Chỉ có thể sửa phiên đấu giá khi còn trạng thái SCHEDULED");
        }

        BigDecimal newStartPrice = request.startPrice() != null ? request.startPrice() : auction.getStartPrice();
        BigDecimal newBidIncrement = request.bidIncrement() != null ? request.bidIncrement() : auction.getBidIncrement();
        BigDecimal newReservePrice = request.reservePrice() != null ? request.reservePrice() : auction.getReservePrice();
        Instant newStartTime = request.startTime() != null ? request.startTime() : auction.getStartTime();
        Instant newEndTime = request.endTime() != null ? request.endTime() : auction.getEndTime();

        Instant now = Instant.now(clock);
        validateScheduleAndPrices(newStartPrice, newBidIncrement, newReservePrice, newStartTime, newEndTime, now);

        auction.setStartPrice(newStartPrice);
        auction.setCurrentPrice(newStartPrice);
        auction.setBidIncrement(newBidIncrement);
        auction.setReservePrice(newReservePrice);
        auction.setStartTime(newStartTime);
        auction.setEndTime(newEndTime);

        Auction updated = auctionRepository.save(auction);

        webSocketPublisher.publishAuctionEvent(
                AuctionEvent.of(AuctionEventType.AUCTION_UPDATED, updated, null, null, now)
        );

        log.info("Updated scheduled auction id={} by sellerId={}", updated.getId(), sellerId);
        return auctionMapper.toDetail(updated, sellerId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MyAuctionItemResponse> getMyAuctions(
            Long sellerId,
            AuctionStatus status,
            String keyword,
            Pageable pageable
    ) {
        SortWhitelist.validate(pageable, MY_AUCTION_SORT_FIELDS);

        Specification<Auction> spec = AuctionSpecification.mineFilter(sellerId, status, keyword);
        Page<Auction> page = auctionRepository.findAll(spec, pageable);
        return auctionMapper.toMyAuctionPage(page);
    }

    @Override
    @Transactional
    public AuctionDetailResponse cancelAuction(Long sellerId, Long auctionId, String reason) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên đấu giá"));

        if (!auction.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không sở hữu phiên đấu giá này");
        }

        if (!auction.isOpen()) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Không thể hủy phiên đấu giá ở trạng thái " + auction.getStatus()
            );
        }

        // BR-08: người bán chỉ được hủy phiên SCHEDULED hoặc ACTIVE khi bidCount = 0
        if (auction.getStatus() == AuctionStatus.ACTIVE
                && auction.getBidCount() != null
                && auction.getBidCount() > 0) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL, "Không thể hủy phiên đang diễn ra đã có lượt đặt giá");
        }

        String trimmedReason = reason != null ? reason.trim() : "";
        if (trimmedReason.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Lý do hủy phiên không được để trống",
                    Map.of("fields", Map.of("reason", "Lý do hủy phiên không được để trống"))
            );
        }

        Auction cancelled = auctionLifecycleSupport.cancelLocked(auction, trimmedReason);
        return auctionMapper.toDetail(cancelled, sellerId);
    }

    @Override
    public void startScheduledAuctions() {
        Instant now = Instant.now(clock);
        List<Long> scheduledIds = auctionRepository.findIdsByStatusAndStartTimeLessThanEqual(AuctionStatus.SCHEDULED, now);

        for (Long id : scheduledIds) {
            try {
                auctionCloser.startOne(id);
            } catch (Exception e) {
                log.error("Failed to start scheduled auction {}", id, e);
            }
        }
    }

    @Override
    public void closeEndedAuctions() {
        Instant now = Instant.now(clock);
        List<Long> activeIds = auctionRepository.findIdsByStatusAndEndTimeLessThanEqual(AuctionStatus.ACTIVE, now);

        for (Long id : activeIds) {
            try {
                auctionCloser.closeOne(id);
            } catch (Exception e) {
                log.error("Failed to close auction {}", id, e);
            }
        }
    }

    private void validateScheduleAndPrices(
            BigDecimal startPrice,
            BigDecimal bidIncrement,
            BigDecimal reservePrice,
            Instant startTime,
            Instant endTime,
            Instant now
    ) {
        if (startPrice == null || startPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Giá khởi điểm phải lớn hơn 0",
                    Map.of("fields", Map.of("startPrice", "Giá khởi điểm phải lớn hơn 0"))
            );
        }
        if (bidIncrement == null || bidIncrement.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Bước giá phải lớn hơn 0",
                    Map.of("fields", Map.of("bidIncrement", "Bước giá phải lớn hơn 0"))
            );
        }
        if (reservePrice != null && reservePrice.compareTo(startPrice) < 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Giá sàn phải lớn hơn hoặc bằng giá khởi điểm",
                    Map.of("fields", Map.of("reservePrice", "Giá sàn phải lớn hơn hoặc bằng giá khởi điểm"))
            );
        }
        if (startTime == null || !startTime.isAfter(now)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thời gian bắt đầu phải ở tương lai",
                    Map.of("fields", Map.of("startTime", "Thời gian bắt đầu phải lớn hơn thời điểm hiện tại"))
            );
        }
        if (endTime == null || !endTime.isAfter(startTime)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thời gian kết thúc phải sau thời gian bắt đầu",
                    Map.of("fields", Map.of("endTime", "Thời gian kết thúc phải sau thời gian bắt đầu"))
            );
        }

        Duration minDuration = appProperties.getAuction().getMinDurationMinutes();
        Duration maxDuration = appProperties.getAuction().getMaxDurationDays();
        Duration duration = Duration.between(startTime, endTime);
        if (duration.compareTo(minDuration) < 0 || duration.compareTo(maxDuration) > 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thời lượng phiên đấu giá không hợp lệ",
                    Map.of("fields", Map.of("endTime", "Thời lượng phiên phải nằm trong khoảng từ "
                            + minDuration.toMinutes() + " phút đến " + maxDuration.toDays() + " ngày"))
            );
        }
    }
}
