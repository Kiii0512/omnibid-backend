package com.omnibid.dto.admin;

import com.omnibid.enums.ProductCondition;
import com.omnibid.enums.ProductStatus;

import java.time.LocalDateTime;

public record AdminProductResponse(
        Long id,
        Long sellerId,
        String sellerName,
        Long categoryId,
        String categoryName,
        String title,
        String description,
        ProductCondition condition,
        ProductStatus status,
        LocalDateTime createdAt
) {}
