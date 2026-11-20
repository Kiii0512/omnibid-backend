package com.omnibid.dto.admin;

import com.omnibid.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        LocalDateTime createdAt
) {}
