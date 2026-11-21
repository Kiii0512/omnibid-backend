package com.omnibid.dto.bid;

import com.omnibid.enums.AuctionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record MyBidResponse(
        Long id,
        Long auctionId,
        String productTitle,
        BigDecimal amount,
        Instant createdAt,
        AuctionStatus auctionStatus,
        boolean leading
) {}
