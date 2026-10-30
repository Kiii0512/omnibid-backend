package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.dto.auction.AuctionCreateRequest;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Bid;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.BidStatus;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.UserRole;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.AuctionService;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionServiceImpl implements AuctionService {

    private final AuctionRepository auctionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final BidRepository bidRepository;
    private final OrderRepository orderRepository;
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

        AuctionResponse response = mapToAuctionResponse(savedAuction);
        webSocketPublisher.publishAuctionUpdate(auctionId, response);
        return response;
    }

    @Override
    @Transactional
    public void startScheduledAuctions() {
        LocalDateTime now = LocalDateTime.now();
        List<Auction> scheduledAuctions = auctionRepository.findByStatusAndStartTimeLessThanEqual(AuctionStatus.SCHEDULED, now);

        for (Auction auction : scheduledAuctions) {
            try {
                auction.setStatus(AuctionStatus.ACTIVE);
                Auction updated = auctionRepository.save(auction);
                AuctionResponse response = mapToAuctionResponse(updated);
                webSocketPublisher.publishAuctionUpdate(updated.getId(), response);
                log.info("Started auction {} at {}", updated.getId(), now);
            } catch (Exception e) {
                log.error("Failed to start scheduled auction {}", auction.getId(), e);
            }
        }
    }

    @Override
    @Transactional
    public void closeEndedAuctions() {
        LocalDateTime now = LocalDateTime.now();
        List<Auction> endedCandidates = auctionRepository.findByStatusAndEndTimeLessThanEqual(AuctionStatus.ACTIVE, now);

        for (Auction candidate : endedCandidates) {
            try {
                closeSingleAuction(candidate.getId(), now);
            } catch (Exception e) {
                log.error("Failed to close auction {}", candidate.getId(), e);
            }
        }
    }

    private void closeSingleAuction(Long auctionId, LocalDateTime now) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE || now.isBefore(auction.getEndTime())) {
            return;
        }

        boolean hasBids = auction.getBidCount() != null && auction.getBidCount() > 0 && auction.getHighestBidderId() != null;
        boolean reserveMet = auction.getReservePrice() == null
                || (auction.getCurrentPrice() != null && auction.getCurrentPrice().compareTo(auction.getReservePrice()) >= 0);

        if (hasBids && reserveMet) {
            auction.setStatus(AuctionStatus.ENDED);
            auction.setWinnerId(auction.getHighestBidderId());
            auction.setFinalPrice(auction.getCurrentPrice());
            auction.setClosedAt(now);
            auctionRepository.save(auction);

            Optional<Bid> winningBid = bidRepository.findTopByAuctionIdAndStatusOrderByAmountDesc(auction.getId(), BidStatus.LEADING);
            winningBid.ifPresent(bid -> {
                bid.setStatus(BidStatus.WON);
                bidRepository.save(bid);
            });

            Product product = productRepository.findById(auction.getProductId()).orElse(null);
            if (product != null) {
                product.setStatus(ProductStatus.RESERVED);
                productRepository.save(product);
            }

            if (orderRepository.findByAuctionId(auction.getId()).isEmpty()) {
                Order order = Order.builder()
                        .auctionId(auction.getId())
                        .buyerId(auction.getHighestBidderId())
                        .amount(auction.getCurrentPrice())
                        .status(OrderStatus.PENDING_PAYMENT)
                        .paymentDeadline(now.plusHours(48))
                        .build();
                orderRepository.save(order);
            }

            log.info("Auction {} ENDED successfully, winner={}", auction.getId(), auction.getWinnerId());
        } else {
            auction.setStatus(AuctionStatus.UNSOLD);
            auction.setClosedAt(now);
            auctionRepository.save(auction);

            Optional<Bid> highestBid = bidRepository.findTopByAuctionIdAndStatusOrderByAmountDesc(auction.getId(), BidStatus.LEADING);
            highestBid.ifPresent(bid -> {
                bid.setStatus(BidStatus.NOT_WON);
                bidRepository.save(bid);
            });

            Product product = productRepository.findById(auction.getProductId()).orElse(null);
            if (product != null) {
                product.setStatus(ProductStatus.AVAILABLE);
                productRepository.save(product);
            }

            log.info("Auction {} UNSOLD (bids={}, reserveMet={})", auction.getId(), hasBids, reserveMet);
        }

        AuctionResponse response = mapToAuctionResponse(auction);
        webSocketPublisher.publishAuctionUpdate(auction.getId(), response);
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
                highestBidderName = bidder.getFullName();
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
                .build();
    }
}

