package com.omnibid.security;

import com.omnibid.enums.UserRole;

import java.time.Instant;

public record JwtClaims(
        Long userId,
        UserRole role,
        int tokenVersion,
        Instant expiresAt
) {}
