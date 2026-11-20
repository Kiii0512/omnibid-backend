package com.omnibid.dto.admin;

import com.omnibid.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserStatusUpdateRequest(
        @NotNull(message = "status is required")
        UserStatus status,

        @NotNull(message = "reason is required")
        @Size(min = 5, message = "reason must be at least 5 characters")
        String reason
) {}
