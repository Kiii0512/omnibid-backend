package com.omnibid.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCancelAuctionRequest(
        @NotBlank(message = "reason is required")
        @Size(min = 5, message = "reason must be at least 5 characters")
        String reason
) {}
