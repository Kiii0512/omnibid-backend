package com.omnibid.dto.user;

import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;

public record UserSummary(
        Long id,
        String fullName,
        String email,
        UserRole role,
        UserStatus status,
        String avatarUrl
) {}
