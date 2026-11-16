package com.omnibid.dto.product;

import com.omnibid.enums.ProductCondition;
import com.omnibid.enums.ProductStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ProductResponse(
        Long id,
        Long sellerId,
        String sellerName,
        Long categoryId,
        String categoryName,
        String title,
        String description,
        ProductCondition condition,
        ProductStatus status,
        List<ProductImageResponse> images,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

