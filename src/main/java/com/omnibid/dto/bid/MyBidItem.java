package com.omnibid.dto.bid;

import com.omnibid.enums.AuctionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record MyBidItem(
        Long id,
        Long auctionId,
        String title,
        String primaryImageUrl,
        BigDecimal amount,
        Instant createdAt,
        AuctionStatus auctionStatus,
        BigDecimal currentPrice,
        Instant endTime,
        boolean leading,
        Long orderId
) {}
