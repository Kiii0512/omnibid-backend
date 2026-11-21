package com.omnibid.dto.admin;

import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String fullName,
        String email,
        String phoneNumber,
        String avatarUrl,
        UserRole role,
        UserStatus status,
        Instant createdAt
) {}
