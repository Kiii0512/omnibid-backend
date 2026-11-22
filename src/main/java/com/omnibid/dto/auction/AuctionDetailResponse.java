package com.omnibid.dto.auction;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.omnibid.dto.product.ProductImageResponse;
import com.omnibid.dto.user.UserRef;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.enums.ProductCondition;
import com.omnibid.enums.ReserveStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AuctionDetailResponse(
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
        UserRef seller,
        String highestBidderName,
        boolean viewerIsSeller,
        boolean viewerIsLeading,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal reservePrice,
        String winnerName,
        BigDecimal finalPrice,
        Instant closedAt,
        String cancelReason,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long orderId
) {}
