package com.omnibid.dto.product;

import java.time.Instant;

public record ProductImageResponse(
        Long id,
        String imageUrl,
        String publicId,
        Boolean isPrimary,
        Instant createdAt
) {}

