package com.omnibid.service;

import com.omnibid.common.MaskingUtils;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.dto.websocket.AuctionEvent;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Order;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionEventType;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.ReserveStatus;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final NotificationService notificationService;
    private final Clock clock;

    @Autowired
    public AuctionCloser(PlatformTransactionManager transactionManager,
                         AuctionRepository auctionRepository,
                         ProductRepository productRepository,
                         UserRepository userRepository,
                         OrderService orderService,
                         WebSocketPublisher webSocketPublisher,
                         NotificationService notificationService,
                         Clock clock) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate = template;
        this.auctionRepository = auctionRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderService = orderService;
        this.webSocketPublisher = webSocketPublisher;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    public AuctionCloser(TransactionTemplate transactionTemplate,
                         AuctionRepository auctionRepository,
                         ProductRepository productRepository,
                         UserRepository userRepository,
                         OrderService orderService,
                         WebSocketPublisher webSocketPublisher,
                         NotificationService notificationService,
                         Clock clock) {
        this.transactionTemplate = transactionTemplate;
        this.auctionRepository = auctionRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
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

        // BR-09: đọc trạng thái người dẫn đầu bằng truy vấn thường (không khóa users)
        User leader = auction.getHighestBidderId() != null
                ? userRepository.findById(auction.getHighestBidderId()).orElse(null)
                : null;
        boolean hasActiveLeader = leader != null
                && leader.getStatus() == UserStatus.ACTIVE
                && auction.getBidCount() != null
                && auction.getBidCount() > 0;
        boolean reserveMet = auction.reserveStatus() != ReserveStatus.NOT_MET;
        String maskedLeaderName = leader != null ? MaskingUtils.maskName(leader.getFullName()) : null;

        if (hasActiveLeader && reserveMet) {
            auction.closeAsEnded(now);
            auctionRepository.save(auction);

            // Thứ tự khóa: auctions -> (order mới tạo, không cần khóa) -> products
            Order order = orderService.createFromAuction(auction, now);

            Product product = productRepository.findByIdForUpdate(auction.getProductId())
                    .or(() -> productRepository.findById(auction.getProductId()))
                    .orElse(null);
            if (product != null && product.getStatus() != com.omnibid.enums.ProductStatus.HIDDEN) {
                product.reserve();
                productRepository.save(product);
            }

            Instant paymentDeadline = order != null ? order.getPaymentDeadline() : null;
            notificationService.notify(
                    auction.getWinnerId(),
                    NotificationType.AUCTION_RESULT,
                    NotificationTemplates.auctionWonTitle(),
                    NotificationTemplates.auctionWonContent(auction.getId(), auction.getFinalPrice(), paymentDeadline),
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
            if (product != null && product.getStatus() != com.omnibid.enums.ProductStatus.HIDDEN) {
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

            log.info("Auction {} UNSOLD (hasActiveLeader={}, reserveMet={})", auction.getId(), hasActiveLeader, reserveMet);
        }

        webSocketPublisher.publishAuctionEvent(
                AuctionEvent.of(AuctionEventType.AUCTION_ENDED, auction, maskedLeaderName, null, now)
        );
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

        webSocketPublisher.publishAuctionEvent(
                AuctionEvent.of(AuctionEventType.AUCTION_STARTED, updated, null, null, now)
        );
    }
}
