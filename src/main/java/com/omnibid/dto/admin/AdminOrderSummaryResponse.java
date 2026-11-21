package com.omnibid.dto.admin;

import com.omnibid.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminOrderSummaryResponse(
        Long id,
        Long auctionId,
        Long productId,
        String productTitle,
        Long buyerId,
        String buyerName,
        Long sellerId,
        String sellerName,
        BigDecimal finalPrice,
        OrderStatus status,
        String paymentStatus,
        String shipmentStatus,
        Instant createdAt
) {}
