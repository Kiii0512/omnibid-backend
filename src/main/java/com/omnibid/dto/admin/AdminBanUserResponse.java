package com.omnibid.dto.admin;

import com.omnibid.enums.UserStatus;

public record AdminBanUserResponse(
        Long id,
        UserStatus status,
        int cancelledAuctions,
        int cancelledOrders
) {}
