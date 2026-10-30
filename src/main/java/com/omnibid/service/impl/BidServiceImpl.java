package com.omnibid.service.impl;

import com.omnibid.common.AppException;
import com.omnibid.dto.bid.BidRequest;
import com.omnibid.dto.bid.BidResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Bid;
import com.omnibid.entity.User;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.BidStatus;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.BidRepository;
import com.omnibid.repository.UserRepository;
import com.omnibid.service.BidService;
import com.omnibid.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

    @Override
    @Transactional
    public BidResponse placeBid(Long bidderId, BidRequest request) {
        Auction auction = auctionRepository.findByIdForUpdate(request.getAuctionId())
                .orElseThrow(() -> new AppException("Auction not found with id: " + request.getAuctionId(), HttpStatus.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if (auction.getStatus() != AuctionStatus.ACTIVE || !now.isBefore(auction.getEndTime())) {
            throw new AppException("Auction is not active or has ended", HttpStatus.BAD_REQUEST);
        }

        if (auction.getSellerId().equals(bidderId)) {
            throw new AppException("Seller cannot bid on their own auction", HttpStatus.BAD_REQUEST);
        }

        if (bidderId.equals(auction.getHighestBidderId())) {
            throw new AppException("You are already the highest bidder", HttpStatus.BAD_REQUEST);
        }

        BigDecimal minRequiredAmount;
        if (auction.getBidCount() == null || auction.getBidCount() == 0) {
            minRequiredAmount = auction.getStartPrice();
        } else {
            minRequiredAmount = auction.getCurrentPrice().add(auction.getBidIncrement());
        }

        if (request.getAmount().compareTo(minRequiredAmount) < 0) {
            throw new AppException("Bid amount must be at least " + minRequiredAmount, HttpStatus.BAD_REQUEST);
        }

        Optional<Bid> currentLeadingBid = bidRepository.findTopByAuctionIdAndStatusOrderByAmountDesc(
                auction.getId(), BidStatus.LEADING);
        currentLeadingBid.ifPresent(leadingBid -> {
            leadingBid.setStatus(BidStatus.OUTBID);
            bidRepository.save(leadingBid);
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

        User bidder = userRepository.findById(bidderId).orElse(null);
        String maskedBidderName = bidder != null ? maskName(bidder.getFullName()) : "***";

        BidResponse bidResponse = BidResponse.builder()
                .id(savedBid.getId())
                .auctionId(savedBid.getAuctionId())
                .bidderId(savedBid.getBidderId())
                .bidderName(maskedBidderName)
                .amount(savedBid.getAmount())
                .status(savedBid.getStatus())
                .createdAt(savedBid.getCreatedAt() != null ? savedBid.getCreatedAt() : LocalDateTime.now())
                .build();

        webSocketPublisher.publishAuctionUpdate(auction.getId(), bidResponse);

        return bidResponse;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BidResponse> getBidsByAuction(Long auctionId) {
        List<Bid> bids = bidRepository.findByAuctionIdOrderByAmountDesc(auctionId);
        return bids.stream()
                .map(this::mapToBidResponse)
                .collect(Collectors.toList());
    }

    private BidResponse mapToBidResponse(Bid bid) {
        User bidder = userRepository.findById(bid.getBidderId()).orElse(null);
        String maskedBidderName = bidder != null ? maskName(bidder.getFullName()) : "***";

        return BidResponse.builder()
                .id(bid.getId())
                .auctionId(bid.getAuctionId())
                .bidderId(bid.getBidderId())
                .bidderName(maskedBidderName)
                .amount(bid.getAmount())
                .status(bid.getStatus())
                .createdAt(bid.getCreatedAt())
                .build();
    }

    private String maskName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "***";
        }
        String name = fullName.trim();
        if (name.length() == 1) {
            return name + "***";
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1);
    }
}

