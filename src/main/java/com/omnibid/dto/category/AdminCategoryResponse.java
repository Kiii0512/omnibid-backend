package com.omnibid.dto.category;

public record AdminCategoryResponse(
        Long id,
        String name,
        String slug,
        String icon,
        long productCount
) {
}
