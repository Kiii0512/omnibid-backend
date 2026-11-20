package com.omnibid.dto.product;

import com.omnibid.enums.ProductCondition;
import jakarta.validation.constraints.Size;

public record ProductUpdateRequest(
        Long categoryId,

        @Size(max = 255, message = "Title cannot exceed 255 characters")
        String title,

        String description,

        ProductCondition condition
) {}

