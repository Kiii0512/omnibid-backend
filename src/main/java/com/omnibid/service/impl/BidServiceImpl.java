package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.MaskingUtils;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.bid.BidRequest;
import com.omnibid.dto.bid.BidResponse;
import com.omnibid.dto.bid.BidSummaryResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Bid;
import com.omnibid.entity.User;
import com.omnibid.enums.NotificationType;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.BidService;
import com.omnibid.service.NotificationService;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidServiceImpl implements BidService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final NotificationService notificationService;
    private final Clock clock;

    @Override
    @Transactional
    public BidResponse placeBid(Long bidderId, BidRequest request) {
        // TODO v4-phase-5: move auctionId to path, use principal status, emit AuctionEvent
        Auction auction = auctionRepository.findByIdForUpdate(request.getAuctionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Auction not found with id: " + request.getAuctionId()));

        User bidder = userRepository.findById(bidderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found with id: " + bidderId));

        if (bidder.isBanned()) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED, "Tài khoản của bạn đã bị khóa");
        }

        Long previousLeaderId = auction.getHighestBidderId();
        Instant now = Instant.now(clock);
        Bid newBid = auction.placeBid(bidderId, request.getAmount(), now);

        Bid savedBid;
        try {
            savedBid = bidRepository.save(newBid);
            bidRepository.flush();
        } catch (DataIntegrityViolationException dive) {
            log.warn("Duplicate bid amount detected at DB level: auctionId={}, amount={}", auction.getId(), request.getAmount());
            BigDecimal nextMin = request.getAmount().add(auction.getBidIncrement());
            throw new BusinessException(ErrorCode.BID_TOO_LOW, "Mức giá đã có người đặt", Map.of("minNextBid", nextMin));
        }

        auctionRepository.save(auction);

        if (previousLeaderId != null && !previousLeaderId.equals(bidderId)) {
            notificationService.notify(
                    previousLeaderId,
                    NotificationType.OUTBID,
                    NotificationTemplates.outbidTitle(),
                    NotificationTemplates.outbidContent(auction.getId(), request.getAmount()),
                    "AUCTION",
                    auction.getId()
            );
        }

        String maskedBidderName = MaskingUtils.maskName(bidder.getFullName());

        BidResponse bidResponse = BidResponse.builder()
                .id(savedBid.getId())
                .auctionId(savedBid.getAuctionId())
                .bidderId(savedBid.getBidderId())
                .bidderName(maskedBidderName)
                .amount(savedBid.getAmount())
                .createdAt(savedBid.getCreatedAt() != null ? savedBid.getCreatedAt() : now)
                .build();

        webSocketPublisher.publishAuctionUpdate(auction.getId(), bidResponse);

        log.info("Bid placed successfully: auctionId={}, bidderId={}, amount={}",
                auction.getId(), bidderId, request.getAmount());

        return bidResponse;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BidResponse> getBidsByAuction(Long auctionId) {
        List<Bid> bids = bidRepository.findByAuctionIdOrderByAmountDesc(auctionId);
        return bids.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BidResponse> getMyBids(Long bidderId, Pageable pageable) {
        Page<Bid> page = bidRepository.findByBidderIdOrderByCreatedAtDesc(bidderId, pageable);
        return PageResponse.from(page, this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public BidSummaryResponse getMyBidSummary(Long bidderId) {
        // TODO v4-phase-5: remove getMyBidSummary (endpoint removed in v4)
        long totalBids = bidRepository.countByBidderId(bidderId);
        long participatingAuctions = bidRepository.countDistinctAuctionsByBidderId(bidderId);
        return new BidSummaryResponse(totalBids, participatingAuctions, 0L, 0L, 0L);
    }

    private BidResponse mapToResponse(Bid bid) {
        String maskedName = "***";
        User bidder = userRepository.findById(bid.getBidderId()).orElse(null);
        if (bidder != null) {
            maskedName = MaskingUtils.maskName(bidder.getFullName());
        }
        return BidResponse.builder()
                .id(bid.getId())
                .auctionId(bid.getAuctionId())
                .bidderId(bid.getBidderId())
                .bidderName(maskedName)
                .amount(bid.getAmount())
                .createdAt(bid.getCreatedAt())
                .build();
    }
}
