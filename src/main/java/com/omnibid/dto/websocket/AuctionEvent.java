package com.omnibid.dto.websocket;

import com.omnibid.dto.bid.BidHistoryItem;
import com.omnibid.enums.AuctionEventType;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ReserveStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record AuctionEvent(
        AuctionEventType type,
        Long auctionId,
        AuctionStatus status,
        BigDecimal currentPrice,
        BigDecimal minNextBid,
        Integer bidCount,
        String highestBidderName,
        ReserveStatus reserveStatus,
        Instant startTime,
        Instant endTime,
        BidHistoryItem bid,
        Instant createdAt
) {}
