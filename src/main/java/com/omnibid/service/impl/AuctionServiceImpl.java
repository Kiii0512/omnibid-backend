package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionDetailResponse;
import com.omnibid.dto.auction.AuctionFilterRequest;
import com.omnibid.dto.auction.AuctionResponse;
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
import com.omnibid.service.AuctionLifecycleService;
import com.omnibid.service.AuctionService;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionServiceImpl implements AuctionService {

    private final AuctionRepository auctionRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;
    private final AuctionMapper auctionMapper;
    private final AuctionLifecycleService auctionLifecycleService;
    private final WebSocketPublisher webSocketPublisher;

    @Override
    @Transactional
    public AuctionResponse createAuction(Long userId, AuctionCreateRequest request) {
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new AppException("Start time must be before end time", HttpStatus.BAD_REQUEST);
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));

        if (!product.getSellerId().equals(userId)) {
            throw new AppException("You do not own this product", HttpStatus.FORBIDDEN);
        }

        if (product.getStatus() != ProductStatus.AVAILABLE) {
            throw new AppException("Product is not available for auction", HttpStatus.BAD_REQUEST);
        }

        if (productImageRepository.countByProductId(product.getId()) < 1) {
            throw new AppException("Product must have at least 1 image before creating an auction", HttpStatus.BAD_REQUEST);
        }

        product.setStatus(ProductStatus.IN_AUCTION);
        productRepository.save(product);

        LocalDateTime now = LocalDateTime.now();
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
        AuctionResponse response = auctionMapper.toAuctionResponse(savedAuction);

        if (initialStatus == AuctionStatus.ACTIVE) {
            publishSafely(savedAuction.getId(), response);
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public AuctionDetailResponse getAuctionById(Long id, Long currentUserId, boolean isAdmin) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new AppException("Auction not found with id: " + id, HttpStatus.NOT_FOUND));
        return auctionMapper.toAuctionDetailResponse(auction, currentUserId, isAdmin);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuctionResponse> getAuctions(AuctionFilterRequest filter, Pageable pageable) {
        Sort effectiveSort = resolveSort(
                filter != null ? filter.getSortBy() : null,
                pageable.getSort()
        );
        Pageable effectivePageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                effectiveSort
        );

        Page<Auction> auctionPage = auctionRepository.findAll(
                AuctionSpecification.withFilter(filter),
                effectivePageable
        );
        return auctionMapper.toAuctionResponsePage(auctionPage);
    }

    @Override
    @Transactional
    public AuctionResponse cancelAuction(Long userId, Long auctionId, String reason) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AppException("Auction not found with id: " + auctionId, HttpStatus.NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found with id: " + userId, HttpStatus.NOT_FOUND));

        if (!auction.getSellerId().equals(userId) && user.getRole() != UserRole.ADMIN) {
            throw new AppException("You are not authorized to cancel this auction", HttpStatus.FORBIDDEN);
        }

        if (auction.getBidCount() != null && auction.getBidCount() > 0) {
            throw new AppException("Cannot cancel auction with existing bids", HttpStatus.BAD_REQUEST);
        }

        if (auction.getStatus() != AuctionStatus.SCHEDULED && auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new AppException("Auction cannot be cancelled in status: " + auction.getStatus(), HttpStatus.BAD_REQUEST);
        }

        auction.setStatus(AuctionStatus.CANCELLED);
        auction.setCancelReason(reason);
        Auction savedAuction = auctionRepository.save(auction);

        Product product = productRepository.findById(auction.getProductId()).orElse(null);
        if (product != null) {
            product.setStatus(ProductStatus.AVAILABLE);
            productRepository.save(product);
        }

        AuctionResponse response = auctionMapper.toAuctionResponse(savedAuction);
        publishSafely(auctionId, response);
        return response;
    }

    @Override
    public void startScheduledAuctions() {
        auctionLifecycleService.startScheduledAuctions();
    }

    @Override
    public void closeEndedAuctions() {
        auctionLifecycleService.closeEndedAuctions();
    }

    private void publishSafely(Long auctionId, AuctionResponse payload) {
        try {
            webSocketPublisher.publishAuctionUpdate(auctionId, payload);
        } catch (Exception e) {
            log.warn("Failed to publish WebSocket update for auction {}: {}", auctionId, e.getMessage());
        }
    }

    private Sort resolveSort(String sortBy, Sort fallbackSort) {
        if (!StringUtils.hasText(sortBy)) {
            return fallbackSort != null && fallbackSort.isSorted()
                    ? fallbackSort
                    : Sort.by(Sort.Direction.ASC, "endTime");
        }
        return switch (sortBy.trim()) {
            case "endingSoon" -> Sort.by(Sort.Direction.ASC, "endTime");
            case "newlyListed" -> Sort.by(Sort.Direction.DESC, "startTime");
            case "priceAsc" -> Sort.by(Sort.Direction.ASC, "currentPrice");
            case "priceDesc" -> Sort.by(Sort.Direction.DESC, "currentPrice");
            case "mostBids" -> Sort.by(Sort.Direction.DESC, "bidCount");
            default -> fallbackSort != null && fallbackSort.isSorted()
                    ? fallbackSort
                    : Sort.by(Sort.Direction.ASC, "endTime");
        };
    }
}
