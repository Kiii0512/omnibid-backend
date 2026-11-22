package com.omnibid.dto.admin;

import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.Map;

public record AdminStatisticsResponse(
        long totalUsers,
        long bannedUsers,
        Map<AuctionStatus, Long> auctionsByStatus,
        Map<OrderStatus, Long> ordersByStatus,
        BigDecimal completedOrderValue,
        long pendingReports
) {}
