package com.omnibid.dto.auth;

import com.omnibid.dto.user.UserSummary;
import com.omnibid.enums.UserRole;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant expiresAt,
        UserRole role,
        UserSummary user
) {}
