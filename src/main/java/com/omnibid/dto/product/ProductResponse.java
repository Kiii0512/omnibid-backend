package com.omnibid.dto.product;

import com.omnibid.enums.ProductCondition;
import com.omnibid.enums.ProductStatus;

import java.time.Instant;
import java.util.List;

public record ProductResponse(
        Long id,
        String title,
        String description,
        Long categoryId,
        String categoryName,
        ProductCondition condition,
        ProductStatus status,
        String primaryImageUrl,
        int imageCount,
        Long activeAuctionId,
        boolean everAuctioned,
        List<ProductImageResponse> images,
        Instant createdAt,
        Instant updatedAt
) {
}
