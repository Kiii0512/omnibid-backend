package com.omnibid.dto.category;

import java.time.LocalDateTime;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String iconUrl,
        LocalDateTime createdAt,
        Long activeAuctionCount
) {
    public CategoryResponse(Long id, String name, String slug, String iconUrl, LocalDateTime createdAt) {
        this(id, name, slug, iconUrl, createdAt, 0L);
    }
}
