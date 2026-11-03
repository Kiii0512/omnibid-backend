package com.omnibid.service;

import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Bid;
import com.omnibid.entity.Product;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.BidStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class AuctionCloser {

    private final TransactionTemplate transactionTemplate;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ProductRepository productRepository;
    private final OrderService orderService;
    private final WebSocketPublisher webSocketPublisher;
    private final Clock clock;

    public AuctionCloser(PlatformTransactionManager transactionManager,
                         AuctionRepository auctionRepository,
                         BidRepository bidRepository,
                         ProductRepository productRepository,
                         OrderService orderService,
                         WebSocketPublisher webSocketPublisher,
                         Clock clock) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate = template;
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.productRepository = productRepository;
        this.orderService = orderService;
        this.webSocketPublisher = webSocketPublisher;
        this.clock = clock;
    }

    // Overloaded constructor for tests or custom transaction template
    public AuctionCloser(TransactionTemplate transactionTemplate,
                         AuctionRepository auctionRepository,
                         BidRepository bidRepository,
                         ProductRepository productRepository,
                         OrderService orderService,
                         WebSocketPublisher webSocketPublisher,
                         Clock clock) {
        this.transactionTemplate = transactionTemplate;
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.productRepository = productRepository;
        this.orderService = orderService;
        this.webSocketPublisher = webSocketPublisher;
        this.clock = clock;
    }

    public void closeOne(Long auctionId) {
        transactionTemplate.executeWithoutResult(status -> doClose(auctionId, LocalDateTime.now(clock)));
    }

    public void doClose(Long auctionId, LocalDateTime now) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE || now.isBefore(auction.getEndTime())) {
            return;
        }

        // 6.1: Chọn Bid hợp lệ cao nhất của bidder ACTIVE
        List<Bid> validBids = bidRepository.findValidBidsDesc(
                auction.getId(),
                List.of(BidStatus.LEADING, BidStatus.OUTBID),
                PageRequest.of(0, 1)
        );

        boolean hasValidBid = !validBids.isEmpty();
        Bid winningBid = hasValidBid ? validBids.get(0) : null;
        boolean reserveMet = winningBid != null && (
                auction.getReservePrice() == null
                || winningBid.getAmount().compareTo(auction.getReservePrice()) >= 0
        );

        if (hasValidBid && reserveMet) {
            auction.setStatus(AuctionStatus.ENDED);
            auction.setWinnerId(winningBid.getBidderId());
            auction.setFinalPrice(winningBid.getAmount());
            auction.setClosedAt(now);
            auctionRepository.save(auction);

            winningBid.setStatus(BidStatus.WON);
            bidRepository.save(winningBid);

            Product product = productRepository.findById(auction.getProductId()).orElse(null);
            if (product != null) {
                product.setStatus(ProductStatus.RESERVED);
                productRepository.save(product);
            }

            orderService.createFromAuction(auction);

            log.info("Auction {} ENDED successfully, winner={}, price={}",
                    auction.getId(), auction.getWinnerId(), auction.getFinalPrice());
        } else {
            auction.setStatus(AuctionStatus.UNSOLD);
            auction.setClosedAt(now);
            auctionRepository.save(auction);

            if (winningBid != null) {
                winningBid.setStatus(BidStatus.NOT_WON);
                bidRepository.save(winningBid);
            }

            Product product = productRepository.findById(auction.getProductId()).orElse(null);
            if (product != null && product.getStatus() != ProductStatus.HIDDEN && product.getStatus() != ProductStatus.REMOVED) {
                product.setStatus(ProductStatus.AVAILABLE);
                productRepository.save(product);
            }

            log.info("Auction {} UNSOLD (hasValidBid={}, reserveMet={})", auction.getId(), hasValidBid, reserveMet);
        }

        publishWsAfterCommit(auction);
    }

    public void startOne(Long auctionId) {
        transactionTemplate.executeWithoutResult(status -> doStart(auctionId, LocalDateTime.now(clock)));
    }

    public void doStart(Long auctionId, LocalDateTime now) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null || auction.getStatus() != AuctionStatus.SCHEDULED || now.isBefore(auction.getStartTime())) {
            return;
        }

        auction.setStatus(AuctionStatus.ACTIVE);
        Auction updated = auctionRepository.save(auction);
        log.info("Started auction {} at {}", updated.getId(), now);

        publishWsAfterCommit(updated);
    }

    private void publishWsAfterCommit(Auction auction) {
        AuctionResponse response = AuctionResponse.builder()
                .id(auction.getId())
                .productId(auction.getProductId())
                .sellerId(auction.getSellerId())
                .startPrice(auction.getStartPrice())
                .bidIncrement(auction.getBidIncrement())
                .currentPrice(auction.getCurrentPrice())
                .highestBidderId(auction.getHighestBidderId())
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

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    webSocketPublisher.publishAuctionUpdate(auction.getId(), response);
                }
            });
        } else {
            webSocketPublisher.publishAuctionUpdate(auction.getId(), response);
        }
    }
}
