package com.omnibid.dto.product;

public record ProductSummaryResponse(
        long total,
        long available,
        long inAuction,
        long sold,
        long hidden
) {}

