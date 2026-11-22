package com.omnibid.service;

import com.omnibid.common.MaskingUtils;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.dto.websocket.AuctionEvent;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionEventType;
import com.omnibid.enums.NotificationType;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionLifecycleSupport {

    private final AuctionRepository auctionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional
    public Auction cancelLocked(Auction auction, String reason) {
        Instant now = Instant.now(clock);
        auction.cancel(reason);
        auction.setClosedAt(now);

        Auction savedAuction = auctionRepository.save(auction);

        // Thứ tự khóa: auctions -> products
        Product product = productRepository.findByIdForUpdate(auction.getProductId())
                .or(() -> productRepository.findById(auction.getProductId()))
                .orElse(null);
        if (product != null && product.getStatus() != com.omnibid.enums.ProductStatus.HIDDEN) {
            product.release();
            productRepository.save(product);
        }

        if (notificationService != null) {
            notificationService.notify(
                    savedAuction.getSellerId(),
                    NotificationType.AUCTION_CANCELLED,
                    NotificationTemplates.auctionCancelledTitle(reason),
                    NotificationTemplates.auctionCancelledContent(savedAuction.getId(), reason),
                    "AUCTION",
                    savedAuction.getId()
            );

            if (savedAuction.getHighestBidderId() != null
                    && !savedAuction.getHighestBidderId().equals(savedAuction.getSellerId())) {
                notificationService.notify(
                        savedAuction.getHighestBidderId(),
                        NotificationType.AUCTION_CANCELLED,
                        NotificationTemplates.auctionCancelledTitle(reason),
                        NotificationTemplates.auctionCancelledContent(savedAuction.getId(), reason),
                        "AUCTION",
                        savedAuction.getId()
                );
            }
        }

        String maskedLeaderName = null;
        if (savedAuction.getHighestBidderId() != null) {
            maskedLeaderName = userRepository.findById(savedAuction.getHighestBidderId())
                    .map(User::getFullName)
                    .map(MaskingUtils::maskName)
                    .orElse(null);
        }

        webSocketPublisher.publishAuctionEvent(
                AuctionEvent.of(AuctionEventType.AUCTION_CANCELLED, savedAuction, maskedLeaderName, null, now)
        );
        log.info("Auction {} CANCELLED successfully. Reason: {}", auction.getId(), reason);
        return savedAuction;
    }
}
