package com.omnibid.dto.bid;

import java.math.BigDecimal;

public record PlaceBidResponse(
        Long bidId,
        Long auctionId,
        BigDecimal amount,
        BigDecimal currentPrice,
        int bidCount,
        BigDecimal minNextBid
) {}
