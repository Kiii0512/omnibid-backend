package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.AuctionCloser;
import com.omnibid.service.AuctionLifecycleSupport;
import com.omnibid.service.AuctionService;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserRepository userRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final AuctionCloser auctionCloser;
    private final AuctionLifecycleSupport auctionLifecycleSupport;
    private final Clock clock;

    @Override
    @Transactional
    public AuctionResponse createAuction(Long userId, AuctionCreateRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (request.getStartTime().isBefore(now)) {
            throw new AppException("VALIDATION_ERROR", "Thời gian bắt đầu không được ở quá khứ", HttpStatus.BAD_REQUEST);
        }
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new AppException("VALIDATION_ERROR", "Start time must be before end time", HttpStatus.BAD_REQUEST);
        }
        Duration duration = Duration.between(request.getStartTime(), request.getEndTime());
        if (duration.toMinutes() < 60 || duration.toDays() > 30) {
            throw new AppException("VALIDATION_ERROR", "Thời lượng phiên phải từ 1 giờ đến 30 ngày", HttpStatus.BAD_REQUEST);
        }
        if (request.getReservePrice() != null && request.getReservePrice().compareTo(request.getStartPrice()) < 0) {
            throw new AppException("VALIDATION_ERROR", "Giá sàn phải lớn hơn hoặc bằng giá khởi điểm", HttpStatus.BAD_REQUEST);
        }

        Product product = productRepository.findByIdForUpdate(request.getProductId())
                .or(() -> productRepository.findById(request.getProductId()))
                .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));

        if (!product.getSellerId().equals(userId)) {
            throw new AppException("You do not own this product", HttpStatus.FORBIDDEN);
        }

        if (product.getStatus() != ProductStatus.AVAILABLE) {
            throw new AppException("PRODUCT_NOT_AVAILABLE", "Product is not available for auction", HttpStatus.CONFLICT);
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
        AuctionResponse response = mapToAuctionResponse(savedAuction);

        if (initialStatus == AuctionStatus.ACTIVE) {
            webSocketPublisher.publishAuctionUpdate(savedAuction.getId(), response);
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public AuctionResponse getAuctionById(Long id) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new AppException("Auction not found with id: " + id, HttpStatus.NOT_FOUND));
        return mapToAuctionResponse(auction);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuctionResponse> getAuctions(AuctionStatus status, Pageable pageable) {
        Page<Auction> auctionPage;
        if (status != null) {
            auctionPage = auctionRepository.findByStatus(status, pageable);
        } else {
            auctionPage = auctionRepository.findAll(pageable);
        }
        return auctionPage.map(this::mapToAuctionResponse);
    }

    @Override
    @Transactional
    public AuctionResponse cancelAuction(Long userId, Long auctionId, String reason) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new AppException("Auction not found with id: " + auctionId, HttpStatus.NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found with id: " + userId, HttpStatus.NOT_FOUND));

        boolean isSeller = auction.getSellerId().equals(userId);
        boolean isAdmin = user.getRole() == UserRole.ADMIN;

        if (!isSeller && !isAdmin) {
            throw new AppException("FORBIDDEN", "You are not authorized to cancel this auction", HttpStatus.FORBIDDEN);
        }

        if (isSeller && auction.getStatus() == AuctionStatus.ACTIVE && auction.getBidCount() != null && auction.getBidCount() > 0) {
            throw new AppException("CANNOT_CANCEL", "Cannot cancel auction with existing bids", HttpStatus.CONFLICT);
        }

        if (auction.getStatus() != AuctionStatus.SCHEDULED && auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new AppException("CANNOT_CANCEL", "Auction cannot be cancelled in status: " + auction.getStatus(), HttpStatus.CONFLICT);
        }

        Auction cancelled = auctionLifecycleSupport.cancelLocked(auction, reason);
        return mapToAuctionResponse(cancelled);
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

    private AuctionResponse mapToAuctionResponse(Auction auction) {
        String productTitle = null;
        String sellerName = null;
        String highestBidderName = null;

        Product product = productRepository.findById(auction.getProductId()).orElse(null);
        if (product != null) {
            productTitle = product.getTitle();
        }

        User seller = userRepository.findById(auction.getSellerId()).orElse(null);
        if (seller != null) {
            sellerName = seller.getFullName();
        }

        if (auction.getHighestBidderId() != null) {
            User bidder = userRepository.findById(auction.getHighestBidderId()).orElse(null);
            if (bidder != null) {
                highestBidderName = maskName(bidder.getFullName());
            }
        }

        return AuctionResponse.builder()
                .id(auction.getId())
                .productId(auction.getProductId())
                .productTitle(productTitle)
                .sellerId(auction.getSellerId())
                .sellerName(sellerName)
                .startPrice(auction.getStartPrice())
                .bidIncrement(auction.getBidIncrement())
                .currentPrice(auction.getCurrentPrice())
                .highestBidderId(auction.getHighestBidderId())
                .highestBidderName(highestBidderName)
                .bidCount(auction.getBidCount())
                .startTime(auction.getStartTime())
                .endTime(auction.getEndTime())
                .status(auction.getStatus())
                .winnerId(auction.getWinnerId())
                .finalPrice(auction.getFinalPrice())
                .closedAt(auction.getClosedAt())
                .cancelReason(auction.getCancelReason())
                .createdAt(auction.getCreatedAt())
                .build();
    }

    private String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "***";
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.length() <= 2) {
                sb.append(p.charAt(0)).append("*");
            } else {
                sb.append(p.charAt(0))
                        .append("*".repeat(p.length() - 2))
                        .append(p.charAt(p.length() - 1));
            }
            if (i < parts.length - 1) sb.append(" ");
        }
        return sb.toString();
    }
}
