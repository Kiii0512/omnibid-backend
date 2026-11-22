package com.omnibid.dto.product;

public record ProductImageResponse(
        Long id,
        String imageUrl,
        boolean isPrimary
) {
}
