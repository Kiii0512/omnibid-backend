package com.omnibid.service;

import com.omnibid.common.MaskingUtils;
import com.omnibid.dto.websocket.AuctionEvent;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionEventType;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
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
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final WebSocketPublisher webSocketPublisher;
    private final Clock clock;

    public AuctionCloser(PlatformTransactionManager transactionManager,
                         AuctionRepository auctionRepository,
                         ProductRepository productRepository,
                         UserRepository userRepository,
                         OrderService orderService,
                         WebSocketPublisher webSocketPublisher,
                         Clock clock) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate = template;
        this.auctionRepository = auctionRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderService = orderService;
        this.webSocketPublisher = webSocketPublisher;
        this.clock = clock;
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
        log.info("Kích hoạt phiên đấu giá thành công: id={}, at={}", updated.getId(), now);

        publishAuctionEvent(updated, AuctionEventType.AUCTION_STARTED, null);
    }

    public void closeOne(Long auctionId) {
        transactionTemplate.executeWithoutResult(status -> doClose(auctionId, Instant.now(clock)));
    }

    public void doClose(Long auctionId, Instant now) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId).orElse(null);
        if (auction == null) {
            log.warn("Không tìm thấy phiên {} khi đóng", auctionId);
            return;
        }

        if (auction.getStatus() != AuctionStatus.ACTIVE || now.isBefore(auction.getEndTime())) {
            return;
        }

        boolean hasBid = auction.getHighestBidderId() != null;
        boolean leaderActive = false;
        if (hasBid) {
            leaderActive = userRepository.findById(auction.getHighestBidderId())
                    .map(u -> u.getStatus() == UserStatus.ACTIVE)
                    .orElse(false);
        }
        boolean reserveMet = auction.getReservePrice() == null
                || (auction.getCurrentPrice() != null && auction.getCurrentPrice().compareTo(auction.getReservePrice()) >= 0);

        if (hasBid && leaderActive && reserveMet) {
            auction.closeAsEnded(now);
            auctionRepository.save(auction);

            Product product = productRepository.findByIdForUpdate(auction.getProductId())
                    .or(() -> productRepository.findById(auction.getProductId()))
                    .orElse(null);
            if (product != null) {
                product.reserve();
                productRepository.save(product);
            }

            orderService.createFromAuction(auction);

            log.info("Chốt phiên thành công: auctionId={}, winnerId={}, finalPrice={}",
                    auction.getId(), auction.getWinnerId(), auction.getFinalPrice());
            publishAuctionEvent(auction, AuctionEventType.AUCTION_ENDED, getMaskedBidderName(auction.getWinnerId()));
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

            log.info("Chốt phiên UNSOLD: auctionId={}, hasBid={}, leaderActive={}, reserveMet={}",
                    auction.getId(), hasBid, leaderActive, reserveMet);
            publishAuctionEvent(auction, AuctionEventType.AUCTION_ENDED, null);
        }
    }

    private String getMaskedBidderName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(u -> MaskingUtils.maskName(u.getFullName())).orElse(null);
    }

    private void publishAuctionEvent(Auction a, AuctionEventType type, String maskedBidderName) {
        AuctionEvent event = AuctionEvent.builder()
                .type(type)
                .auctionId(a.getId())
                .status(a.getStatus())
                .currentPrice(a.getCurrentPrice())
                .minNextBid(a.minNextBid())
                .bidCount(a.getBidCount())
                .highestBidderName(maskedBidderName)
                .reserveStatus(a.reserveStatus())
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .createdAt(Instant.now(clock))
                .build();
        webSocketPublisher.publishAuctionEvent(event);
    }
}
