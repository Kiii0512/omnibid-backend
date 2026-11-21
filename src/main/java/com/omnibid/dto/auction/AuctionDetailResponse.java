package com.omnibid.dto.auction;

import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductCondition;
import com.omnibid.enums.ReserveStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Builder
public record AuctionDetailResponse(
        Long id,
        Long productId,
        String title,
        String description,
        Long categoryId,
        String categoryName,
        ProductCondition condition,
        Long sellerId,
        String sellerName,
        BigDecimal startPrice,
        BigDecimal bidIncrement,
        BigDecimal currentPrice,
        BigDecimal minNextBid,
        BigDecimal reservePrice,
        ReserveStatus reserveStatus,
        String highestBidderName,
        Integer bidCount,
        Instant startTime,
        Instant endTime,
        AuctionStatus status,
        Long winnerId,
        BigDecimal finalPrice,
        Instant closedAt,
        String cancelReason,
        String primaryImageUrl,
        List<ProductImageResponse> images,
        Instant createdAt
) {}
