package com.omnibid.dto.product;

import com.omnibid.enums.ProductCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(
        @NotNull(message = "Category ID is required")
        Long categoryId,

        @NotBlank(message = "Title cannot be blank")
        @Size(max = 255, message = "Title cannot exceed 255 characters")
        String title,

        @NotBlank(message = "Description cannot be blank")
        String description,

        @NotNull(message = "Condition is required")
        ProductCondition condition
) {}

