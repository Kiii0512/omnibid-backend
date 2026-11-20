package com.omnibid.dto.admin;

import com.omnibid.enums.ProductStatus;

import java.util.List;

public record ProductHideResponse(
        Long productId,
        ProductStatus status,
        List<Long> cancelledAuctionIds,
        List<Long> cancelledOrderIds
) {}
