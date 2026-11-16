package com.omnibid.dto.bid;

public record BidSummaryResponse(
        long totalBids,
        long participatingAuctions,
        long leadingAuctions,
        long wonAuctions,
        long lostAuctions
) {}

