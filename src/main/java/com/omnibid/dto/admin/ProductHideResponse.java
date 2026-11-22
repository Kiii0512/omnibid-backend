package com.omnibid.dto.admin;

import com.omnibid.enums.ProductStatus;

public record ProductHideResponse(
        Long id,
        ProductStatus status,
        int cancelledAuctions,
        int cancelledOrders
) {}
