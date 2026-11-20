package com.omnibid.dto.admin;

import com.omnibid.enums.ProductStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductHideRequest(
        @NotNull(message = "status is required")
        ProductStatus status,

        @NotNull(message = "reason is required")
        @Size(min = 5, message = "reason must be at least 5 characters")
        String reason
) {}
