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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionLifecycleSupport {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ProductRepository productRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final Clock clock;

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
