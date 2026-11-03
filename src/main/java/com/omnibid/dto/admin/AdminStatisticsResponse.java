package com.omnibid.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record AdminStatisticsResponse(
        RangeDto range,
        UserStats users,
        Map<String, Long> products,
        AuctionStats auctions,
        BidStats bids,
        Map<String, Long> orders,
        RevenueStats revenue,
        PaymentStats payments,
        Map<String, Long> reports
) {
    public record RangeDto(LocalDateTime from, LocalDateTime to) {}
    public record UserStats(long total, long active, long banned, long newInRange) {}
    public record AuctionStats(long scheduled, long active, long ended, long unsold, long cancelled, double sellThroughRate) {}
    public record BidStats(long total, long inRange, double avgPerAuction) {}
    public record RevenueStats(boolean simulated, BigDecimal completedOrderTotal) {}
    public record PaymentStats(long success, long failed, long pending, double successRate) {}
}
