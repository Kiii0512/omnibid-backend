package com.omnibid.dto.product;

import com.omnibid.enums.ProductCondition;
import jakarta.validation.constraints.Size;

public record ProductUpdateRequest(
        Long categoryId,

        @Size(max = 255, message = "Title cannot exceed 255 characters")
        String title,

        @Size(max = 10000, message = "Description cannot exceed 10000 characters")
        String description,

        ProductCondition condition
) {}

