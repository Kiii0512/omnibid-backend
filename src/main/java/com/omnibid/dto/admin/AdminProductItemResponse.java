package com.omnibid.dto.admin;

import com.omnibid.dto.user.UserRef;
import com.omnibid.enums.ProductCondition;
import com.omnibid.enums.ProductStatus;

import java.time.Instant;

public record AdminProductItemResponse(
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
        Instant createdAt,
        UserRef seller
) {}
