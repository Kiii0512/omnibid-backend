package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.common.MaskingUtils;
import com.omnibid.common.NotificationTemplates;
import com.omnibid.dto.bid.BidRequest;
import com.omnibid.dto.bid.BidResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Bid;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.BidStatus;
import com.omnibid.enums.NotificationType;
import com.omnibid.enums.UserStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.BidService;
import com.omnibid.service.NotificationService;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        User bidder = userRepository.findById(bidderId)
                .orElseThrow(() -> new AppException("USER_NOT_FOUND", "User not found with id: " + bidderId, HttpStatus.NOT_FOUND));

        if (bidder.getStatus() == UserStatus.BANNED) {
            throw new AppException("ACCOUNT_BANNED", "Tài khoản của bạn đã bị khóa", HttpStatus.FORBIDDEN);
        }

        Auction auction = auctionRepository.findByIdForUpdate(request.getAuctionId())
                .orElseThrow(() -> new AppException("AUCTION_NOT_FOUND", "Auction not found with id: " + request.getAuctionId(), HttpStatus.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        if (auction.getStatus() != AuctionStatus.ACTIVE || !now.isBefore(auction.getEndTime())) {
            throw new AppException("AUCTION_NOT_ACTIVE", "Auction is not active or has ended", HttpStatus.CONFLICT);
        }

        if (auction.getSellerId().equals(bidderId)) {
            throw new AppException("SELF_BID_NOT_ALLOWED", "Seller cannot bid on their own auction", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        if (bidderId.equals(auction.getHighestBidderId())) {
            throw new AppException("ALREADY_LEADING", "You are already the highest bidder", HttpStatus.CONFLICT);
        }

        BigDecimal minRequiredAmount;
        if (auction.getBidCount() == null || auction.getBidCount() == 0) {
            minRequiredAmount = auction.getStartPrice();
        } else {
            minRequiredAmount = auction.getCurrentPrice().add(auction.getBidIncrement());
        }

        if (request.getAmount().compareTo(minRequiredAmount) < 0) {
            throw new AppException("BID_TOO_LOW", "Bid amount must be at least " + minRequiredAmount, HttpStatus.CONFLICT, Map.of("minNextBid", minRequiredAmount));
        }

        Optional<Bid> currentLeadingBid = bidRepository.findTopByAuctionIdAndStatusOrderByAmountDesc(
                auction.getId(), BidStatus.LEADING);
        currentLeadingBid.ifPresent(leadingBid -> {
            leadingBid.setStatus(BidStatus.OUTBID);
            bidRepository.save(leadingBid);

            // N-01 / UT-CORE-02: Thông báo OUTBID cho người vừa mất vị trí dẫn đầu
            notificationService.notify(
                    leadingBid.getBidderId(),
                    NotificationType.OUTBID,
                    NotificationTemplates.outbidTitle(),
                    NotificationTemplates.outbidContent(auction.getId(), request.getAmount()),
                    "AUCTION",
                    auction.getId()
            );
        });

        Bid newBid = Bid.builder()
                .auctionId(auction.getId())
                .bidderId(bidderId)
                .amount(request.getAmount())
                .status(BidStatus.LEADING)
                .build();
        Bid savedBid = bidRepository.save(newBid);

        auction.setCurrentPrice(request.getAmount());
        auction.setHighestBidderId(bidderId);
        auction.setBidCount((auction.getBidCount() == null ? 0 : auction.getBidCount()) + 1);
        auctionRepository.save(auction);

        String maskedBidderName = MaskingUtils.maskName(bidder.getFullName());

        BidResponse bidResponse = BidResponse.builder()
                .id(savedBid.getId())
                .auctionId(savedBid.getAuctionId())
                .bidderId(savedBid.getBidderId())
                .bidderName(maskedBidderName)
                .amount(savedBid.getAmount())
                .status(savedBid.getStatus())
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
        return bids.stream().map(bid -> {
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
                    .status(bid.getStatus())
                    .createdAt(bid.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }
}
