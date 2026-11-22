package com.omnibid.dto.auction;

import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ReserveStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AuctionSummaryResponse(
        Long id,
        Long productId,
        String title,
        String primaryImageUrl,
        Long categoryId,
        String categoryName,
        AuctionStatus status,
        BigDecimal startPrice,
        BigDecimal currentPrice,
        BigDecimal minNextBid,
        int bidCount,
        ReserveStatus reserveStatus,
        Instant startTime,
        Instant endTime,
        Instant createdAt
) {}
