package com.omnibid.dto.product;

import com.omnibid.enums.ProductCondition;
import com.omnibid.enums.ProductStatus;

import java.time.Instant;

public record ProductListItemResponse(
        Long id,
        String title,
        Long categoryId,
        String categoryName,
        ProductCondition condition,
        ProductStatus status,
        String primaryImageUrl,
        int imageCount,
        Long activeAuctionId,
        boolean everAuctioned,
        Instant createdAt
) {
}
