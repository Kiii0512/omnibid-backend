package com.omnibid.dto.category;

import java.time.Instant;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String icon,
        Instant createdAt,
        Long activeAuctionCount
) {
    public CategoryResponse(Long id, String name, String slug, String icon, Instant createdAt) {
        this(id, name, slug, icon, createdAt, 0L);
    }
}
