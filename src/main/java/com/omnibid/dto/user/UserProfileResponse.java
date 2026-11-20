package com.omnibid.dto.user;

import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String fullName,
        String email,
        String phoneNumber,
        String avatarUrl,
        String address,
        UserRole role,
        UserStatus status,
        LocalDateTime createdAt
) {}

