package com.omnibid.service;

import com.omnibid.common.NotificationTemplates;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Bid;
import com.omnibid.entity.Product;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.BidStatus;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
public class AuctionLifecycleSupport {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ProductRepository productRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final NotificationService notificationService;
    private final Clock clock;

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
        LocalDateTime now = LocalDateTime.now(clock);
        auction.setStatus(AuctionStatus.CANCELLED);
        auction.setCancelReason(reason);
        auction.setClosedAt(now);

        // LEADING bid chuyển thành NOT_WON khi hủy phiên
        Optional<Bid> leadingBid = bidRepository.findTopByAuctionIdAndStatusOrderByAmountDesc(
                auction.getId(), BidStatus.LEADING);
        leadingBid.ifPresent(bid -> {
            bid.setStatus(BidStatus.NOT_WON);
            bidRepository.save(bid);

            // N-05 / D-05: Thông báo AUCTION_ENDED cho người đang dẫn đầu khi phiên bị hủy
            if (notificationService != null) {
                notificationService.notify(
                        bid.getBidderId(),
                        NotificationType.AUCTION_ENDED,
                        NotificationTemplates.auctionCancelledTitle(reason),
                        NotificationTemplates.auctionCancelledContent(auction.getId(), reason),
                        "AUCTION",
                        auction.getId()
                );
            }
        });

        Auction savedAuction = auctionRepository.save(auction);

        Product product = productRepository.findById(auction.getProductId()).orElse(null);
        if (product != null && product.getStatus() != ProductStatus.HIDDEN && product.getStatus() != ProductStatus.REMOVED) {
            product.setStatus(ProductStatus.AVAILABLE);
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
