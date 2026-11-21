package com.omnibid.dto.auth;

import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant expiresAt,
        UserRole role,
        UserSummary user
) {
    public record UserSummary(
            Long id,
            String fullName,
            String email,
            UserRole role,
            UserStatus status,
            String avatarUrl
    ) {}
}
