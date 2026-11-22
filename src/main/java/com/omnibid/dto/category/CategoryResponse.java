package com.omnibid.dto.category;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String icon
) {
}
