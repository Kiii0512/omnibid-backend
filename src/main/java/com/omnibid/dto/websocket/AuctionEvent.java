package com.omnibid.dto.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.omnibid.dto.bid.BidHistoryItem;
import com.omnibid.entity.Auction;
import com.omnibid.enums.AuctionEventType;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ReserveStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AuctionEvent(
        AuctionEventType type,
        Long auctionId,
        AuctionStatus status,
        BigDecimal currentPrice,
        BigDecimal minNextBid,
        int bidCount,
        String highestBidderName,
        ReserveStatus reserveStatus,
        Instant startTime,
        Instant endTime,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BidHistoryItem bid,
        Instant createdAt
) {

    public static AuctionEvent of(
            AuctionEventType type,
            Auction auction,
            String highestBidderName,
            BidHistoryItem bid,
            Instant createdAt
    ) {
        return new AuctionEvent(
                type,
                auction.getId(),
                auction.getStatus(),
                auction.getCurrentPrice(),
                auction.minNextBid(),
                auction.getBidCount() != null ? auction.getBidCount() : 0,
                highestBidderName,
                auction.reserveStatus(),
                auction.getStartTime(),
                auction.getEndTime(),
                bid,
                createdAt
        );
    }
}
