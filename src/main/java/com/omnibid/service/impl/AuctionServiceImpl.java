package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.auction.AuctionUpdateRequest;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.mapper.AuctionMapper;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.ProductImageRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.repository.specification.AuctionSpecification;
import com.omnibid.service.AuctionCloser;
import com.omnibid.service.AuctionLifecycleSupport;
import com.omnibid.service.AuctionService;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionServiceImpl implements AuctionService {

    private final AuctionRepository auctionRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final AuctionCloser auctionCloser;
    private final AuctionLifecycleSupport auctionLifecycleSupport;
    private final AuctionMapper auctionMapper;
    private final Clock clock;

    @Override
    @Transactional
    public AuctionResponse createAuction(Long userId, AuctionCreateRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (request.getStartTime().isBefore(now)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thời gian bắt đầu không được ở quá khứ");
        }
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Start time must be before end time");
        }
        Duration duration = Duration.between(request.getStartTime(), request.getEndTime());
        if (duration.toMinutes() < 60 || duration.toDays() > 30) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thời lượng phiên phải từ 1 giờ đến 30 ngày");
        }
        if (request.getReservePrice() != null && request.getReservePrice().compareTo(request.getStartPrice()) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Giá sàn phải lớn hơn hoặc bằng giá khởi điểm");
        }

        Product product = productRepository.findByIdForUpdate(request.getProductId())
                .or(() -> productRepository.findById(request.getProductId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You do not own this product");
        }

        if (product.getStatus() != ProductStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE, "Product is not available for auction");
        }

        if (productImageRepository.countByProductId(product.getId()) < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Product must have at least one image before creating an auction");
        }

        product.setStatus(ProductStatus.IN_AUCTION);
        productRepository.save(product);

        AuctionStatus initialStatus = !request.getStartTime().isAfter(now)
                ? AuctionStatus.ACTIVE
                : AuctionStatus.SCHEDULED;

        Auction auction = Auction.builder()
                .productId(product.getId())
                .sellerId(userId)
                .startPrice(request.getStartPrice())
                .bidIncrement(request.getBidIncrement())
                .reservePrice(request.getReservePrice())
                .currentPrice(request.getStartPrice())
                .highestBidderId(null)
                .bidCount(0)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(initialStatus)
                .build();

        Auction savedAuction = auctionRepository.save(auction);
        AuctionResponse response = auctionMapper.toAuctionResponse(savedAuction, userId);

        if (initialStatus == AuctionStatus.ACTIVE) {
            webSocketPublisher.publishAuctionUpdate(savedAuction.getId(), response);
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public AuctionResponse getAuctionById(Long id) {
        return getAuctionById(id, null);
    }

    @Override
    @Transactional(readOnly = true)
    public AuctionResponse getAuctionById(Long id, Long currentUserId) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Auction not found with id: " + id));
        return auctionMapper.toAuctionResponse(auction, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuctionResponse> getAuctions(AuctionStatus status, Pageable pageable) {
        Page<Auction> auctionPage = (status != null)
                ? auctionRepository.findByStatus(status, pageable)
                : auctionRepository.findAll(pageable);
        return auctionPage.map(a -> auctionMapper.toAuctionResponse(a, null));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuctionResponse> searchAuctions(
            Long currentUserId,
            String q,
            List<Long> categoryIds,
            AuctionStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Long sellerId,
            String sort,
            Pageable pageable
    ) {
        Sort customSort = resolveSort(sort, pageable);
        Pageable resolvedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), customSort);

        Specification<Auction> spec = AuctionSpecification.filter(q, categoryIds, status, minPrice, maxPrice, sellerId);
        Page<Auction> page = auctionRepository.findAll(spec, resolvedPageable);

        return auctionMapper.toAuctionResponsePage(page, currentUserId);
    }

    @Override
    @Transactional
    public AuctionResponse updateAuction(Long sellerId, Long auctionId, AuctionUpdateRequest request) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Auction not found with id: " + auctionId));

        if (!auction.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You do not own this auction");
        }

        boolean isScheduled = auction.getStatus() == AuctionStatus.SCHEDULED;
        boolean isActiveNoBids = auction.getStatus() == AuctionStatus.ACTIVE && (auction.getBidCount() == null || auction.getBidCount() == 0);
        if (!isScheduled && !isActiveNoBids) {
            throw new BusinessException(ErrorCode.AUCTION_NOT_EDITABLE, "Auction can only be updated when SCHEDULED or ACTIVE with no bids");
        }

        if (request.startPrice() != null) {
            if (request.startPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Start price must be greater than 0");
            }
            auction.setStartPrice(request.startPrice());
            if (auction.getBidCount() == null || auction.getBidCount() == 0) {
                auction.setCurrentPrice(request.startPrice());
            }
        }

        if (request.bidIncrement() != null) {
            if (request.bidIncrement().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Bid increment must be greater than 0");
            }
            auction.setBidIncrement(request.bidIncrement());
        }

        if (request.reservePrice() != null) {
            if (request.reservePrice().compareTo(auction.getStartPrice()) < 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Reserve price must be greater than or equal to start price");
            }
            auction.setReservePrice(request.reservePrice());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (request.startTime() != null) {
            if (!isScheduled) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cannot change start time of an ACTIVE auction");
            }
            if (request.startTime().isBefore(now)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Start time cannot be in the past");
            }
            auction.setStartTime(request.startTime());
        }

        if (request.endTime() != null) {
            if (!request.endTime().isAfter(auction.getStartTime())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "End time must be after start time");
            }
            Duration d = Duration.between(auction.getStartTime(), request.endTime());
            if (d.toMinutes() < 60 || d.toDays() > 30) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Auction duration must be between 1 hour and 30 days");
            }
            auction.setEndTime(request.endTime());
        }

        Auction updated = auctionRepository.save(auction);
        return auctionMapper.toAuctionResponse(updated, sellerId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuctionResponse> getMyAuctions(Long sellerId, AuctionStatus status, Pageable pageable) {
        Page<Auction> page = (status != null)
                ? auctionRepository.findBySellerIdAndStatus(sellerId, status, pageable)
                : auctionRepository.findBySellerId(sellerId, pageable);
        return auctionMapper.toAuctionResponsePage(page, sellerId);
    }

    @Override
    @Transactional
    public AuctionResponse cancelAuction(Long userId, Long auctionId, String reason) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Auction not found with id: " + auctionId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found with id: " + userId));

        boolean isSeller = auction.getSellerId().equals(userId);
        boolean isAdmin = user.getRole() == UserRole.ADMIN;

        if (!isSeller && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You are not authorized to cancel this auction");
        }

        if (isSeller && auction.getStatus() == AuctionStatus.ACTIVE && auction.getBidCount() != null && auction.getBidCount() > 0) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL, "Cannot cancel auction with existing bids");
        }

        if (auction.getStatus() != AuctionStatus.SCHEDULED && auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL, "Auction cannot be cancelled in status: " + auction.getStatus());
        }

        Auction cancelled = auctionLifecycleSupport.cancelLocked(auction, reason);
        return auctionMapper.toAuctionResponse(cancelled, userId);
    }

    @Override
    public void startScheduledAuctions() {
        LocalDateTime now = LocalDateTime.now(clock);
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
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> activeIds = auctionRepository.findIdsByStatusAndEndTimeLessThanEqual(AuctionStatus.ACTIVE, now);

        for (Long id : activeIds) {
            try {
                auctionCloser.closeOne(id);
            } catch (Exception e) {
                log.error("Failed to close auction {}", id, e);
            }
        }
    }

    private Sort resolveSort(String sort, Pageable pageable) {
        if ("endingSoon".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.ASC, "endTime");
        } else if ("newlyListed".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        } else if ("priceAsc".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.ASC, "currentPrice");
        } else if ("priceDesc".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "currentPrice");
        } else if ("mostBids".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "bidCount");
        }
        return pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt");
    }
}
