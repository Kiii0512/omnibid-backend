package com.omnibid.service.impl;

import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Bid;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.BidStatus;
import com.omnibid.enums.OrderStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.mapper.AuctionMapper;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.OrderRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.service.AuctionLifecycleService;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionLifecycleServiceImpl implements AuctionLifecycleService {

    private final AuctionRepository auctionRepository;
    private final ProductRepository productRepository;
    private final BidRepository bidRepository;
    private final OrderRepository orderRepository;
    private final AuctionMapper auctionMapper;
    private final WebSocketPublisher webSocketPublisher;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void startScheduledAuctions() {
        LocalDateTime now = LocalDateTime.now();
        List<Auction> scheduledAuctions = auctionRepository.findByStatusAndStartTimeLessThanEqual(
                AuctionStatus.SCHEDULED, now);
        if (scheduledAuctions.isEmpty()) {
            return;
        }

        log.info("Found {} scheduled auctions to activate at {}", scheduledAuctions.size(), now);

        for (Auction candidate : scheduledAuctions) {
            try {
                Auction activatedAuction = executeInTransaction(() -> activateSingleAuction(candidate.getId(), now));
                if (activatedAuction != null) {
                    publishUpdateSafely(activatedAuction);
                }
            } catch (OptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict while starting scheduled auction {}, will retry next cycle: {}",
                        candidate.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("Failed to start scheduled auction {}: {}", candidate.getId(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void closeEndedAuctions() {
        LocalDateTime now = LocalDateTime.now();
        List<Auction> endedCandidates = auctionRepository.findByStatusAndEndTimeLessThanEqual(
                AuctionStatus.ACTIVE, now);
        if (endedCandidates.isEmpty()) {
            return;
        }

        log.info("Found {} active auctions past endTime ({}) to close", endedCandidates.size(), now);

        for (Auction candidate : endedCandidates) {
            try {
                Auction closedAuction = executeInTransaction(() -> closeSingleAuction(candidate.getId(), now));
                if (closedAuction == null) {
                    continue;
                }

                if (closedAuction.getStatus() == AuctionStatus.ENDED) {
                    createOrderSafely(closedAuction, now);
                }

                publishUpdateSafely(closedAuction);
            } catch (OptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict while closing auction {}, will retry next cycle: {}",
                        candidate.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("Failed to close ended auction {}: {}", candidate.getId(), e.getMessage(), e);
            }
        }
    }

    private Auction activateSingleAuction(Long auctionId, LocalDateTime now) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null
                || auction.getStatus() != AuctionStatus.SCHEDULED
                || auction.getStartTime() == null
                || auction.getStartTime().isAfter(now)) {
            return null;
        }

        auction.setStatus(AuctionStatus.ACTIVE);
        Auction updatedAuction = auctionRepository.save(auction);

        Product product = productRepository.findById(updatedAuction.getProductId()).orElse(null);
        if (product != null && product.getStatus() != ProductStatus.IN_AUCTION) {
            product.setStatus(ProductStatus.IN_AUCTION);
            productRepository.save(product);
        }

        log.info("Activated auction id={} (productId={} -> IN_AUCTION) -> ACTIVE at {}",
                updatedAuction.getId(), updatedAuction.getProductId(), now);

        return updatedAuction;
    }

    private Auction closeSingleAuction(Long auctionId, LocalDateTime now) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null
                || auction.getStatus() != AuctionStatus.ACTIVE
                || auction.getEndTime() == null
                || auction.getEndTime().isAfter(now)) {
            return null;
        }

        boolean hasBids = auction.getHighestBidderId() != null
                && auction.getBidCount() != null
                && auction.getBidCount() > 0;
        boolean reserveMet = auctionMapper.isReserveReached(auction);

        if (!hasBids || !reserveMet) {
            return markAuctionUnsold(auction, hasBids, reserveMet, now);
        }
        return markAuctionEnded(auction, now);
    }

    private Auction markAuctionUnsold(Auction auction, boolean hasBids, boolean reserveMet, LocalDateTime now) {
        auction.setStatus(AuctionStatus.UNSOLD);
        auction.setClosedAt(now);
        Auction savedAuction = auctionRepository.save(auction);

        if (hasBids) {
            Optional<Bid> highestBid = bidRepository.findTopByAuctionIdAndStatusOrderByAmountDesc(
                    auction.getId(), BidStatus.LEADING);
            highestBid.ifPresent(bid -> {
                bid.setStatus(BidStatus.NOT_WON);
                bidRepository.save(bid);
            });
        }

        Product product = productRepository.findById(auction.getProductId()).orElse(null);
        if (product != null) {
            product.setStatus(ProductStatus.AVAILABLE);
            productRepository.save(product);
        }

        log.info("Closed auction id={} -> UNSOLD (productId={} -> AVAILABLE, hasBids={}, reserveMet={})",
                savedAuction.getId(), savedAuction.getProductId(), hasBids, reserveMet);

        return savedAuction;
    }

    private Auction markAuctionEnded(Auction auction, LocalDateTime now) {
        BigDecimal finalPrice = auction.getCurrentPrice() != null
                ? auction.getCurrentPrice()
                : auction.getStartPrice();

        auction.setStatus(AuctionStatus.ENDED);
        auction.setWinnerId(auction.getHighestBidderId());
        auction.setFinalPrice(finalPrice);
        auction.setClosedAt(now);
        Auction savedAuction = auctionRepository.save(auction);

        Optional<Bid> winningBid = bidRepository.findTopByAuctionIdAndStatusOrderByAmountDesc(
                auction.getId(), BidStatus.LEADING);
        winningBid.ifPresent(bid -> {
            bid.setStatus(BidStatus.WON);
            bidRepository.save(bid);
        });

        Product product = productRepository.findById(auction.getProductId()).orElse(null);
        if (product != null) {
            product.setStatus(ProductStatus.SOLD);
            productRepository.save(product);
        }

        log.info("Closed auction id={} -> ENDED (productId={} -> SOLD, winnerId={}, finalPrice={})",
                savedAuction.getId(), savedAuction.getProductId(), savedAuction.getWinnerId(), savedAuction.getFinalPrice());

        return savedAuction;
    }

    private void createOrderSafely(Auction closedAuction, LocalDateTime now) {
        try {
            executeInTransaction(() -> {
                if (orderRepository.findByAuctionId(closedAuction.getId()).isEmpty()) {
                    Order order = Order.builder()
                            .auctionId(closedAuction.getId())
                            .buyerId(closedAuction.getWinnerId())
                            .amount(closedAuction.getFinalPrice())
                            .status(OrderStatus.PENDING_PAYMENT)
                            .paymentDeadline(now.plusHours(48))
                            .build();
                    orderRepository.save(order);
                    log.info("Created PENDING_PAYMENT order for ended auction id={}, buyerId={}, amount={}",
                            closedAuction.getId(), closedAuction.getWinnerId(), closedAuction.getFinalPrice());
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Auction id={} transitioned to ENDED, but failed to create Order: {}",
                    closedAuction.getId(), e.getMessage(), e);
        }
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> action.get());
        }
        return action.get();
    }

    private void publishUpdateSafely(Auction auction) {
        try {
            AuctionResponse payload = auctionMapper.toAuctionResponse(auction);
            webSocketPublisher.publishAuctionUpdate(auction.getId(), payload);
        } catch (Exception e) {
            log.warn("Failed to publish WebSocket update for auction {}: {}", auction.getId(), e.getMessage());
        }
    }
}

