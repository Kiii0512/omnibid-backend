package com.omnibid.service;

import com.omnibid.common.NotificationTemplates;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.ReserveStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
public class AuctionCloser {

    private final TransactionTemplate transactionTemplate;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ProductRepository productRepository;
    private final OrderService orderService;
    private final WebSocketPublisher webSocketPublisher;
    private final NotificationService notificationService;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AuctionCloser(PlatformTransactionManager transactionManager,
                         AuctionRepository auctionRepository,
                         BidRepository bidRepository,
                         ProductRepository productRepository,
                         OrderService orderService,
                         WebSocketPublisher webSocketPublisher,
                         NotificationService notificationService,
                         Clock clock) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate = template;
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.productRepository = productRepository;
        this.orderService = orderService;
        this.webSocketPublisher = webSocketPublisher;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    public AuctionCloser(TransactionTemplate transactionTemplate,
                         AuctionRepository auctionRepository,
                         BidRepository bidRepository,
                         ProductRepository productRepository,
                         OrderService orderService,
                         WebSocketPublisher webSocketPublisher,
                         NotificationService notificationService,
                         Clock clock) {
        this.transactionTemplate = transactionTemplate;
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.productRepository = productRepository;
        this.orderService = orderService;
        this.webSocketPublisher = webSocketPublisher;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    public void closeOne(Long auctionId) {
        transactionTemplate.executeWithoutResult(status -> doClose(auctionId, Instant.now(clock)));
    }

    public void doClose(Long auctionId, Instant now) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null) {
            log.warn("Auction {} not found when closing", auctionId);
            return;
        }

        if (auction.getStatus() != AuctionStatus.ACTIVE || now.isBefore(auction.getEndTime())) {
            log.info("Auction {} status is {} or not yet expired (endTime={}), skipping close",
                    auctionId, auction.getStatus(), auction.getEndTime());
            return;
        }

        // TODO v4-phase-5: verify highestBidderId is ACTIVE per BR-09
        boolean hasLeader = auction.getHighestBidderId() != null && auction.getBidCount() != null && auction.getBidCount() > 0;
        boolean reserveMet = auction.reserveStatus() != ReserveStatus.NOT_MET;

        if (hasLeader && reserveMet) {
            auction.closeAsEnded(now);
            auctionRepository.save(auction);

            Product product = productRepository.findByIdForUpdate(auction.getProductId())
                    .or(() -> productRepository.findById(auction.getProductId()))
                    .orElse(null);
            if (product != null) {
                product.reserve();
                productRepository.save(product);
            }

            Order order = orderService.createFromAuction(auction);

            notificationService.notify(
                    auction.getWinnerId(),
                    NotificationType.AUCTION_RESULT,
                    NotificationTemplates.auctionWonTitle(),
                    NotificationTemplates.auctionWonContent(auction.getId(), auction.getFinalPrice()),
                    "AUCTION",
                    auction.getId()
            );

            notificationService.notify(
                    auction.getSellerId(),
                    NotificationType.AUCTION_RESULT,
                    NotificationTemplates.auctionEndedSellerTitle(),
                    NotificationTemplates.auctionEndedSellerContent(auction.getId(), auction.getFinalPrice()),
                    "AUCTION",
                    auction.getId()
            );

            log.info("Auction {} ENDED successfully, winner={}, price={}, orderId={}",
                    auction.getId(), auction.getWinnerId(), auction.getFinalPrice(), order != null ? order.getId() : null);
        } else {
            auction.closeAsUnsold(now);
            auctionRepository.save(auction);

            Product product = productRepository.findByIdForUpdate(auction.getProductId())
                    .or(() -> productRepository.findById(auction.getProductId()))
                    .orElse(null);
            if (product != null) {
                product.release();
                productRepository.save(product);
            }

            notificationService.notify(
                    auction.getSellerId(),
                    NotificationType.AUCTION_RESULT,
                    NotificationTemplates.auctionUnsoldSellerTitle(),
                    NotificationTemplates.auctionUnsoldSellerContent(auction.getId()),
                    "AUCTION",
                    auction.getId()
            );

            log.info("Auction {} UNSOLD (hasLeader={}, reserveMet={})", auction.getId(), hasLeader, reserveMet);
        }

        publishWsAfterCommit(auction);
    }

    public void startOne(Long auctionId) {
        transactionTemplate.executeWithoutResult(status -> doStart(auctionId, Instant.now(clock)));
    }

    public void doStart(Long auctionId, Instant now) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null || auction.getStatus() != AuctionStatus.SCHEDULED || now.isBefore(auction.getStartTime())) {
            return;
        }

        auction.activate(now);
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

        webSocketPublisher.publishAuctionUpdate(auction.getId(), response);
    }
}
