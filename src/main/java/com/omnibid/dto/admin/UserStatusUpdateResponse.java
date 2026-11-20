package com.omnibid.dto.admin;

import com.omnibid.enums.UserStatus;

import java.util.Map;

public record UserStatusUpdateResponse(
        Long userId,
        UserStatus status,
        Map<String, Object> effects
) {}
