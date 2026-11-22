package com.omnibid.dto.admin;

import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.dto.user.UserSummary;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductCondition;
import com.omnibid.enums.ProductStatus;
import com.omnibid.enums.ReserveStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminAuctionDetailResponse(
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
        Instant createdAt,
        String description,
        ProductCondition condition,
        List<ProductImageResponse> images,
        BigDecimal bidIncrement,
        BigDecimal reservePrice,
        UserSummary seller,
        UserSummary highestBidder,
        UserSummary winner,
        BigDecimal finalPrice,
        Instant closedAt,
        String cancelReason,
        ProductStatus productStatus,
        Long orderId
) {}
