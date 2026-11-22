package com.omnibid.service;

import com.omnibid.common.NotificationTemplates;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Product;
import com.omnibid.enums.NotificationType;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
public class AuctionLifecycleSupport {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ProductRepository productRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final NotificationService notificationService;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AuctionLifecycleSupport(AuctionRepository auctionRepository,
                                   BidRepository bidRepository,
                                   ProductRepository productRepository,
                                   WebSocketPublisher webSocketPublisher,
                                   NotificationService notificationService,
                                   Clock clock) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.productRepository = productRepository;
        this.webSocketPublisher = webSocketPublisher;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    public AuctionLifecycleSupport(AuctionRepository auctionRepository,
                                   BidRepository bidRepository,
                                   ProductRepository productRepository,
                                   WebSocketPublisher webSocketPublisher,
                                   Clock clock) {
        this(auctionRepository, bidRepository, productRepository, webSocketPublisher, null, clock);
    }

    @Transactional
    public Auction cancelLocked(Auction auction, String reason) {
        Instant now = Instant.now(clock);
        auction.cancel(reason);
        auction.setClosedAt(now);

        if (notificationService != null && auction.getHighestBidderId() != null) {
            notificationService.notify(
                    auction.getHighestBidderId(),
                    NotificationType.AUCTION_CANCELLED,
                    NotificationTemplates.auctionCancelledTitle(reason),
                    NotificationTemplates.auctionCancelledContent(auction.getId(), reason),
                    "AUCTION",
                    auction.getId()
            );
        }

        Auction savedAuction = auctionRepository.save(auction);

        Product product = productRepository.findByIdForUpdate(auction.getProductId())
                .or(() -> productRepository.findById(auction.getProductId()))
                .orElse(null);
        if (product != null) {
            product.release();
            productRepository.save(product);
        }

        publishWsAfterCommit(savedAuction);
        log.info("Auction {} CANCELLED successfully. Reason: {}", auction.getId(), reason);
        return savedAuction;
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

        webSocketPublisher.publishAuctionUpdate(auction.getId(), response);
    }
}
