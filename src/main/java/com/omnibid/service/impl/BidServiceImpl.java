package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.MaskingUtils;
import com.omnibid.common.PageResponse;
import com.omnibid.common.SortWhitelist;
import com.omnibid.dto.bid.BidHistoryItem;
import com.omnibid.dto.bid.BidRequest;
import com.omnibid.dto.bid.MyBidResponse;
import com.omnibid.dto.bid.PlaceBidResponse;
import com.omnibid.dto.websocket.AuctionEvent;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Bid;
import com.omnibid.entity.Product;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionEventType;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.ProductRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.BidService;
import com.omnibid.service.NotificationService;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidServiceImpl implements BidService {

    private final AuctionRepository auctionRepository;
    private final ProductRepository productRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final NotificationService notificationService;
    private final com.omnibid.common.AfterCommitExecutor afterCommitExecutor;
    private final Clock clock;

    private static final Set<String> ALLOWED_BID_SORTS = Set.of("createdAt", "amount");

    @Override
    @Transactional
    public PlaceBidResponse placeBid(Long bidderId, Long auctionId, BidRequest request) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Phiên đấu giá không tồn tại"));

        Product product = productRepository.findById(auction.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        if (product.getStatus() == ProductStatus.HIDDEN) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Phiên đấu giá không tồn tại");
        }

        User bidder = userRepository.findById(bidderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Người dùng không tồn tại"));

        Instant now = Instant.now(clock);
        Long previousLeaderId = auction.getHighestBidderId();

        Bid newBid = auction.placeBid(bidderId, request.amount(), now);

        Bid savedBid;
        try {
            savedBid = bidRepository.saveAndFlush(newBid);
            auctionRepository.save(auction);
        } catch (DataIntegrityViolationException dive) {
            log.warn("Tranh chấp giá đấu tại DB: auctionId={}, amount={}", auctionId, request.amount());
            throw new BusinessException(ErrorCode.CONFLICT, "Mức giá đã được đặt bởi người khác trong cùng thời điểm");
        }

        String maskedBidderName = MaskingUtils.maskName(bidder.getFullName());
        BidHistoryItem bidItem = new BidHistoryItem(savedBid.getId(), savedBid.getAmount(), maskedBidderName, savedBid.getCreatedAt());

        AuctionEvent event = AuctionEvent.builder()
                .type(AuctionEventType.BID_PLACED)
                .auctionId(auction.getId())
                .status(auction.getStatus())
                .currentPrice(auction.getCurrentPrice())
                .minNextBid(auction.minNextBid())
                .bidCount(auction.getBidCount())
                .highestBidderName(maskedBidderName)
                .reserveStatus(auction.reserveStatus())
                .startTime(auction.getStartTime())
                .endTime(auction.getEndTime())
                .bid(bidItem)
                .createdAt(now)
                .build();
        webSocketPublisher.publishAuctionEvent(event);

        if (previousLeaderId != null && !previousLeaderId.equals(bidderId)) {
            Long outbidUserId = previousLeaderId;
            BigDecimal highestBidAmount = request.amount();
            Long aId = auction.getId();
            afterCommitExecutor.execute(() -> notificationService.notify(
                    outbidUserId,
                    com.omnibid.enums.NotificationType.OUTBID,
                    com.omnibid.common.NotificationTemplates.outbidTitle(),
                    com.omnibid.common.NotificationTemplates.outbidContent(aId, highestBidAmount),
                    "AUCTION",
                    aId
            ));
        }

        log.info("Đặt giá thành công: auctionId={}, bidderId={}, amount={}", auctionId, bidderId, request.amount());

        return new PlaceBidResponse(
                savedBid.getId(),
                auction.getId(),
                savedBid.getAmount(),
                auction.getCurrentPrice(),
                auction.getBidCount(),
                auction.minNextBid()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BidHistoryItem> getBidsByAuction(Long auctionId, Pageable pageable) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Phiên đấu giá không tồn tại"));

        Product product = productRepository.findById(auction.getProductId()).orElse(null);
        if (product == null || product.getStatus() == ProductStatus.HIDDEN) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Phiên đấu giá không tồn tại");
        }

        Pageable effectivePageable = resolvePageable(pageable, Sort.by(Sort.Direction.DESC, "createdAt"), ALLOWED_BID_SORTS);
        Page<Bid> page = bidRepository.findByAuctionId(auctionId, effectivePageable);

        return PageResponse.from(page, bid -> {
            String name = userRepository.findById(bid.getBidderId())
                    .map(u -> MaskingUtils.maskName(u.getFullName()))
                    .orElse("***");
            return new BidHistoryItem(bid.getId(), bid.getAmount(), name, bid.getCreatedAt());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MyBidResponse> getMyBids(Long bidderId, Long auctionId, Pageable pageable) {
        Page<Bid> page = bidRepository.findMyBids(bidderId, auctionId, pageable);

        return PageResponse.from(page, bid -> {
            Auction auc = auctionRepository.findById(bid.getAuctionId()).orElse(null);
            String title = auc != null
                    ? productRepository.findById(auc.getProductId()).map(Product::getTitle).orElse(null)
                    : null;
            AuctionStatus status = auc != null ? auc.getStatus() : null;
            boolean leading = auc != null && bidderId.equals(auc.getHighestBidderId());

            return new MyBidResponse(bid.getId(), bid.getAuctionId(), title, bid.getAmount(), bid.getCreatedAt(), status, leading);
        });
    }

    private Pageable resolvePageable(Pageable pageable, Sort defaultSort, Set<String> allowedSorts) {
        if (pageable.getSort().isSorted()) {
            SortWhitelist.validate(pageable, allowedSorts);
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    }
}
