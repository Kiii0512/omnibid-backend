package com.omnibid.dto.admin;

import com.omnibid.enums.UserStatus;

public record AdminUnbanUserResponse(
        Long id,
        UserStatus status
) {}
